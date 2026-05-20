#!/usr/bin/env python3
import base64
import hashlib
import hmac
import json
import os
import subprocess
import tempfile
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, List

MAX_BODY_BYTES = 512 * 1024
MAX_TESTS = 200
MAX_TIMEOUT_MS = 60_000
MIN_TIMEOUT_MS = 50
MAX_VALIDATOR_TIMEOUT_MS = 5_000
MAX_MEMORY_LIMIT_MB = 2048
MIN_MEMORY_LIMIT_MB = 16
MAX_INPUT_CHARS = 64_000
MAX_EXPECTED_OUTPUT_CHARS = 64_000
MAX_VALIDATOR_CODE_CHARS = 120_000

NODE_ID = os.getenv("SANDBOX_NODE_ID", "sandbox-node")
IMAGE_HASH = os.getenv("SANDBOX_IMAGE_HASH", "unknown")
SANDBOX_VERSION = os.getenv("SANDBOX_VERSION", "1")
ATTESTATION_SECRET = os.getenv("SANDBOX_ATTESTATION_SECRET", "")
PORT = int(os.getenv("SANDBOX_PORT", "8080"))

RUNNER_FILE_HASH = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
ACTIVE_RUNS: Dict[str, "RunControl"] = {}
ACTIVE_RUNS_LOCK = threading.Lock()

HARNESS_CODE = """\
import kotlin.system.exitProcess

fun main() {
    val entryClass = Class.forName("SolutionKt")

    val solveMethod = entryClass.methods.firstOrNull { method ->
        method.name == "solve" &&
            method.parameterCount == 1 &&
            method.parameterTypes[0] == String::class.java
    }
    if (solveMethod != null) {
        val input = generateSequence(::readLine).joinToString("\\n")
        val output = solveMethod.invoke(null, input)?.toString().orEmpty()
        print(output)
        return
    }

    val mainWithArray = entryClass.methods.firstOrNull { method ->
        method.name == "main" &&
            method.parameterCount == 1 &&
            method.parameterTypes[0] == Array<String>::class.java
    }
    if (mainWithArray != null) {
        @Suppress("UNCHECKED_CAST")
        mainWithArray.invoke(null, arrayOf<String>())
        return
    }

    val mainNoArgs = entryClass.methods.firstOrNull { method ->
        method.name == "main" && method.parameterCount == 0
    }
    if (mainNoArgs != null) {
        mainNoArgs.invoke(null)
        return
    }

    System.err.println("No supported entrypoint found. Provide solve(input: String): String or main().")
    exitProcess(1)
}
"""

JAVA_HARNESS_CODE = """\
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.stream.Collectors;

public class Harness {
    public static void main(String[] args) throws Exception {
        Class<?> entryClass = Class.forName("Solution");

        Method solveMethod = null;
        for (Method method : entryClass.getMethods()) {
            if (
                method.getName().equals("solve") &&
                method.getParameterCount() == 1 &&
                method.getParameterTypes()[0] == String.class
            ) {
                solveMethod = method;
                break;
            }
        }
        if (solveMethod != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String input = reader.lines().collect(Collectors.joining("\\n"));
            Object output = solveMethod.invoke(null, input);
            System.out.print(output == null ? "" : output.toString());
            return;
        }

        try {
            Method mainWithArray = entryClass.getMethod("main", String[].class);
            mainWithArray.invoke(null, (Object) new String[0]);
            return;
        } catch (NoSuchMethodException ignored) {
        }

        try {
            Method mainNoArgs = entryClass.getMethod("main");
            mainNoArgs.invoke(null);
            return;
        } catch (NoSuchMethodException ignored) {
        }

        throw new IllegalStateException("No supported entrypoint found. Provide solve(String): String or main().");
    }
}
"""

VALIDATOR_HARNESS_CODE = """\
import java.util.Base64

private fun decode(encoded: String): String {
    return String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
}

fun main() {
    val encodedInput = readLine() ?: ""
    val encodedExpected = readLine() ?: ""
    val encodedActual = readLine() ?: ""

    val input = decode(encodedInput)
    val expected = decode(encodedExpected)
    val actual = decode(encodedActual)

    val passed = try {
        validate(input, expected, actual)
    } catch (error: Throwable) {
        System.err.print(error.message ?: error::class.simpleName ?: "Validator error")
        kotlin.system.exitProcess(1)
    }
    print(if (passed) "true" else "false")
}
"""


class RunCancelled(Exception):
    pass


class RunControl:
    def __init__(self) -> None:
        self.cancelled = threading.Event()
        self._lock = threading.Lock()
        self._processes: List[subprocess.Popen[str]] = []

    def register_process(self, process: subprocess.Popen[str]) -> None:
        with self._lock:
            if self.cancelled.is_set():
                raise RunCancelled("Run cancelled.")
            self._processes.append(process)

    def unregister_process(self, process: subprocess.Popen[str]) -> None:
        with self._lock:
            self._processes = [active for active in self._processes if active is not process]

    def throw_if_cancelled(self) -> None:
        if self.cancelled.is_set():
            raise RunCancelled("Run cancelled.")

    def cancel(self) -> None:
        if self.cancelled.is_set():
            return
        self.cancelled.set()
        with self._lock:
            processes = list(self._processes)
        for process in processes:
            try:
                process.kill()
            except Exception:
                pass


def _register_run(run_id: str) -> RunControl:
    control = RunControl()
    with ACTIVE_RUNS_LOCK:
        ACTIVE_RUNS[run_id] = control
    return control


def _finish_run(run_id: str, control: RunControl) -> None:
    with ACTIVE_RUNS_LOCK:
        if ACTIVE_RUNS.get(run_id) is control:
            ACTIVE_RUNS.pop(run_id, None)


def _cancel_run(run_id: str) -> bool:
    with ACTIVE_RUNS_LOCK:
        control = ACTIVE_RUNS.pop(run_id, None)
    if control is None:
        return False
    control.cancel()
    return True


def _json_response(handler: BaseHTTPRequestHandler, status: int, payload: Dict[str, Any]) -> None:
    raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(raw)))
    handler.end_headers()
    handler.wfile.write(raw)


def _normalize_timeout_ms(timeout_ms: Any) -> int:
    try:
        parsed = int(timeout_ms)
    except Exception:
        parsed = 1000
    if parsed < MIN_TIMEOUT_MS:
        return MIN_TIMEOUT_MS
    if parsed > MAX_TIMEOUT_MS:
        return MAX_TIMEOUT_MS
    return parsed


def _normalize_memory_limit_mb(memory_limit_mb: Any) -> int:
    try:
        parsed = int(memory_limit_mb)
    except Exception:
        parsed = 256
    if parsed < MIN_MEMORY_LIMIT_MB:
        return MIN_MEMORY_LIMIT_MB
    if parsed > MAX_MEMORY_LIMIT_MB:
        return MAX_MEMORY_LIMIT_MB
    return parsed


def _normalize_output(value: str) -> str:
    return value.replace("\r\n", "\n").rstrip()


def _compute_run_hash(payload: Dict[str, Any]) -> str:
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return "0x" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _compute_result_hash(results: List[Dict[str, Any]]) -> str:
    ordered = sorted(results, key=lambda item: int(item.get("order", 0)))
    # Execution time is informative but non-deterministic across nodes, so keep it out of consensus.
    canonical = "\n".join(
        (
            f"{int(item.get('id', 0))}|{int(item.get('order', 0))}|{str(item.get('status', ''))}|"
            f"{str(item.get('output', '') or '')}|{str(bool(item.get('passed', False))).lower()}|"
            f"{str(item.get('message', '') or '')}"
        )
        for item in ordered
    )
    return "0x" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _compute_attestation_payload_hash(node_id: str, image_hash: str, run_hash: str, result_hash: str, executed_at: str) -> str:
    payload = f"{node_id}|{image_hash}|{run_hash}|{result_hash}|{executed_at}"
    return "0x" + hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _sign_attestation(payload_hash: str) -> str | None:
    if not ATTESTATION_SECRET:
        return None
    signature = hmac.new(
        ATTESTATION_SECRET.encode("utf-8"),
        payload_hash.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    return "0x" + signature


def _run_subprocess(
    command: List[str],
    cwd: str,
    input_text: str | None = None,
    timeout_seconds: float | None = None,
    run_control: RunControl | None = None,
) -> Dict[str, Any]:
    process = subprocess.Popen(
        command,
        stdin=subprocess.PIPE if input_text is not None else None,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd=cwd,
    )
    if run_control is not None:
        try:
            run_control.register_process(process)
        except RunCancelled:
            process.kill()
            process.wait(timeout=0.5)
            raise

    result: Dict[str, Any] = {}

    def communicate() -> None:
        try:
            stdout, stderr = process.communicate(input=input_text)
            result["stdout"] = stdout
            result["stderr"] = stderr
        except Exception as exc:
            result["exception"] = exc

    worker = threading.Thread(target=communicate, daemon=True)
    worker.start()
    started = time.perf_counter()

    try:
        while worker.is_alive():
            if run_control is not None and run_control.cancelled.is_set():
                process.kill()
                worker.join(timeout=0.5)
                raise RunCancelled("Run cancelled.")
            if timeout_seconds is not None and (time.perf_counter() - started) >= timeout_seconds:
                process.kill()
                worker.join(timeout=0.5)
                return {
                    "timed_out": True,
                    "returncode": process.returncode,
                    "stdout": result.get("stdout", ""),
                    "stderr": result.get("stderr", ""),
                }
            worker.join(timeout=0.01)
        worker.join(timeout=0.01)
        if "exception" in result:
            raise result["exception"]
        return {
            "timed_out": False,
            "returncode": process.returncode,
            "stdout": result.get("stdout", ""),
            "stderr": result.get("stderr", ""),
        }
    finally:
        if run_control is not None:
            run_control.unregister_process(process)


def _compile_solution(
    workdir: Path,
    source_code: str,
    language: str,
    run_control: RunControl | None = None,
) -> Dict[str, Any]:
    normalized_language = language.strip().lower()
    classes_path = workdir / "classes"
    classes_path.mkdir(parents=True, exist_ok=True)

    if normalized_language == "kotlin":
        source_path = workdir / "Solution.kt"
        harness_path = workdir / "Harness.kt"
        source_path.write_text(source_code, encoding="utf-8")
        harness_path.write_text(HARNESS_CODE, encoding="utf-8")
        process = _run_subprocess(
            command=["kotlinc", str(source_path), str(harness_path), "-d", str(classes_path)],
            cwd=str(workdir),
            run_control=run_control,
        )
        if process["returncode"] != 0:
            message = (process["stderr"] or process["stdout"] or "Compilation failed.").strip()
            return {"ok": False, "message": message}
        return {
            "ok": True,
            "classes_path": str(classes_path),
            "entrypoint": "HarnessKt",
            "command": ["kotlin", "-classpath", str(classes_path), "HarnessKt"],
        }

    if normalized_language == "java":
        source_path = workdir / "Solution.java"
        harness_path = workdir / "Harness.java"
        source_path.write_text(source_code, encoding="utf-8")
        harness_path.write_text(JAVA_HARNESS_CODE, encoding="utf-8")
        process = _run_subprocess(
            command=["javac", "-d", str(classes_path), str(source_path), str(harness_path)],
            cwd=str(workdir),
            run_control=run_control,
        )
        if process["returncode"] != 0:
            message = (process["stderr"] or process["stdout"] or "Compilation failed.").strip()
            return {"ok": False, "message": message}
        return {
            "ok": True,
            "classes_path": str(classes_path),
            "entrypoint": "Harness",
            "command": ["java", "-cp", str(classes_path), "Harness"],
        }

    return {"ok": False, "message": f"Unsupported language '{language}'."}


def _compile_validator(
    workdir: Path,
    validator_code: str,
    validator_language: str,
    validator_hash: str,
    run_control: RunControl | None = None,
) -> Dict[str, Any]:
    if validator_language.lower() != "kotlin":
        return {"ok": False, "message": f"Unsupported validator language '{validator_language}'."}

    validator_source = workdir / f"Validator_{validator_hash}.kt"
    validator_harness = workdir / f"ValidatorHarness_{validator_hash}.kt"
    validator_jar = workdir / f"validator_{validator_hash}.jar"
    validator_source.write_text(validator_code, encoding="utf-8")
    validator_harness.write_text(VALIDATOR_HARNESS_CODE, encoding="utf-8")

    process = _run_subprocess(
        command=["kotlinc", str(validator_source), str(validator_harness), "-include-runtime", "-d", str(validator_jar)],
        cwd=str(workdir),
        run_control=run_control,
    )
    if process["returncode"] != 0:
        message = (process["stderr"] or process["stdout"] or "Validator compilation failed.").strip()
        return {"ok": False, "message": message}
    return {"ok": True, "jar_path": str(validator_jar)}


def _read_process_memory_kb(pid: int) -> int | None:
    status_path = Path(f"/proc/{pid}/status")
    try:
        lines = status_path.read_text(encoding="utf-8").splitlines()
    except Exception:
        return None
    current_kb = None
    peak_kb = None
    for line in lines:
        if line.startswith("VmRSS:"):
            parts = line.split()
            if len(parts) >= 2 and parts[1].isdigit():
                current_kb = int(parts[1])
        elif line.startswith("VmHWM:"):
            parts = line.split()
            if len(parts) >= 2 and parts[1].isdigit():
                peak_kb = int(parts[1])
    return peak_kb or current_kb


def _communicate_with_metrics(
    command: List[str],
    input_text: str,
    cwd: str,
    timeout_seconds: float,
    run_control: RunControl | None = None,
) -> Dict[str, Any]:
    started = time.perf_counter()
    process = subprocess.Popen(
        command,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd=cwd,
    )
    if run_control is not None:
        try:
            run_control.register_process(process)
        except RunCancelled:
            process.kill()
            process.wait(timeout=0.5)
            raise
    peak_memory_kb = 0
    stop_sampling = threading.Event()
    initial_sample = _read_process_memory_kb(process.pid)
    if initial_sample is not None:
        peak_memory_kb = max(peak_memory_kb, initial_sample)

    def sample_memory() -> None:
        nonlocal peak_memory_kb
        while not stop_sampling.is_set():
            sampled = _read_process_memory_kb(process.pid)
            if sampled is not None:
                peak_memory_kb = max(peak_memory_kb, sampled)
            if process.poll() is not None:
                break
            time.sleep(0.01)
        sampled = _read_process_memory_kb(process.pid)
        if sampled is not None:
            peak_memory_kb = max(peak_memory_kb, sampled)

    sampler = threading.Thread(target=sample_memory, daemon=True)
    sampler.start()
    result: Dict[str, Any] = {}

    def communicate() -> None:
        try:
            stdout, stderr = process.communicate(input=input_text)
            result["stdout"] = stdout
            result["stderr"] = stderr
        except Exception as exc:
            result["exception"] = exc

    worker = threading.Thread(target=communicate, daemon=True)
    worker.start()
    timed_out = False

    try:
        while worker.is_alive():
            if run_control is not None and run_control.cancelled.is_set():
                process.kill()
                worker.join(timeout=0.5)
                raise RunCancelled("Run cancelled.")
            if (time.perf_counter() - started) >= timeout_seconds:
                process.kill()
                worker.join(timeout=0.5)
                timed_out = True
                break
            worker.join(timeout=0.01)
        worker.join(timeout=0.01)
        if "exception" in result:
            raise result["exception"]
        stdout = result.get("stdout", "")
        stderr = result.get("stderr", "")
        returncode = process.returncode
    finally:
        stop_sampling.set()
        sampler.join(timeout=0.2)
        if run_control is not None:
            run_control.unregister_process(process)

    return {
        "timed_out": timed_out,
        "returncode": returncode,
        "stdout": stdout,
        "stderr": stderr,
        "executionTimeMs": int((time.perf_counter() - started) * 1000),
        "memoryUsedKb": peak_memory_kb or None,
    }


def _execute_test(
    command: List[str],
    test: Dict[str, Any],
    workdir: str,
    run_control: RunControl | None = None,
) -> Dict[str, Any]:
    timeout_ms = _normalize_timeout_ms(test.get("timeoutMs"))
    memory_limit_mb = _normalize_memory_limit_mb(test.get("memoryLimitMb"))
    base_command = list(command)
    if base_command and base_command[0] == "kotlin":
        base_command.insert(1, f"-J-Xmx{memory_limit_mb}m")
    elif base_command and base_command[0] == "java":
        base_command.insert(1, f"-Xmx{memory_limit_mb}m")
    try:
        execution = _communicate_with_metrics(
            command=base_command,
            input_text=str(test.get("inputData", "")),
            cwd=workdir,
            timeout_seconds=timeout_ms / 1000.0,
            run_control=run_control,
        )
        if execution["timed_out"]:
            return {
                "id": int(test["id"]),
                "order": int(test["order"]),
                "status": "TIMEOUT",
                "output": None,
                "passed": False,
                "executionTimeMs": execution["executionTimeMs"],
                "memoryUsedKb": execution["memoryUsedKb"],
                "message": "Execution timed out.",
            }
        if execution["returncode"] != 0:
            return {
                "id": int(test["id"]),
                "order": int(test["order"]),
                "status": "ERROR",
                "output": None,
                "passed": False,
                "executionTimeMs": execution["executionTimeMs"],
                "memoryUsedKb": execution["memoryUsedKb"],
                "message": (
                    execution["stderr"] or execution["stdout"] or f"Process exited with code {execution['returncode']}"
                ).strip(),
            }
        return {
            "id": int(test["id"]),
            "order": int(test["order"]),
            "status": "OK",
            "output": execution["stdout"],
            "passed": False,
            "executionTimeMs": execution["executionTimeMs"],
            "memoryUsedKb": execution["memoryUsedKb"],
            "message": None,
        }
    except RunCancelled:
        raise
    except Exception as exc:
        return {
            "id": int(test["id"]),
            "order": int(test["order"]),
            "status": "ERROR",
            "output": None,
            "passed": False,
            "executionTimeMs": 0,
            "memoryUsedKb": None,
            "message": str(exc),
        }


def _execute_validator(
    validator_jar_path: str,
    test: Dict[str, Any],
    actual_output: str,
    run_control: RunControl | None = None,
) -> Dict[str, Any]:
    timeout_ms = min(_normalize_timeout_ms(test.get("timeoutMs")), MAX_VALIDATOR_TIMEOUT_MS)
    encoded_input = base64.b64encode(str(test.get("inputData", "")).encode("utf-8")).decode("ascii")
    encoded_expected = base64.b64encode(str(test.get("expectedOutput", "")).encode("utf-8")).decode("ascii")
    encoded_actual = base64.b64encode(actual_output.encode("utf-8")).decode("ascii")
    payload = f"{encoded_input}\n{encoded_expected}\n{encoded_actual}\n"
    started = time.perf_counter()
    try:
        result = _run_subprocess(
            command=["java", "-jar", validator_jar_path],
            cwd=str(Path(validator_jar_path).parent),
            input_text=payload,
            timeout_seconds=timeout_ms / 1000.0,
            run_control=run_control,
        )
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        if result["timed_out"]:
            return {"ok": False, "passed": False, "message": "Validator timed out.", "executionTimeMs": elapsed_ms}
        if result["returncode"] != 0:
            message = (result["stderr"] or result["stdout"] or "Validator execution failed.").strip()
            return {"ok": False, "passed": False, "message": message, "executionTimeMs": elapsed_ms}
        verdict_raw = (result["stdout"] or "").strip().lower()
        if verdict_raw == "true":
            return {"ok": True, "passed": True, "message": None, "executionTimeMs": elapsed_ms}
        if verdict_raw == "false":
            return {"ok": True, "passed": False, "message": "Validator returned false.", "executionTimeMs": elapsed_ms}
        return {
            "ok": False,
            "passed": False,
            "message": f"Validator returned invalid verdict '{verdict_raw}'.",
            "executionTimeMs": elapsed_ms,
        }
    except RunCancelled:
        raise
    except Exception as exc:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return {"ok": False, "passed": False, "message": str(exc), "executionTimeMs": elapsed_ms}


def _judge_test_result(
    execution: Dict[str, Any],
    test: Dict[str, Any],
    validator_jars_by_key: Dict[str, str],
    validator_compile_errors: Dict[str, str],
    run_control: RunControl | None = None,
) -> Dict[str, Any]:
    if execution.get("status") != "OK":
        execution["passed"] = False
        return execution

    validator_code = str(test.get("validatorCode", "") or "")
    expected_output = str(test.get("expectedOutput", "") or "")
    if validator_code:
        key = f"{test.get('validatorLanguage', 'kotlin').lower()}:{hashlib.sha256(validator_code.encode('utf-8')).hexdigest()}"
        compile_error = validator_compile_errors.get(key)
        if compile_error:
            execution["status"] = "ERROR"
            execution["passed"] = False
            execution["message"] = compile_error
            return execution
        validator_jar = validator_jars_by_key.get(key)
        if not validator_jar:
            execution["status"] = "ERROR"
            execution["passed"] = False
            execution["message"] = "Validator is not compiled."
            return execution
        verdict = _execute_validator(
            validator_jar_path=validator_jar,
            test=test,
            actual_output=str(execution.get("output") or ""),
            run_control=run_control,
        )
        execution["executionTimeMs"] = int(execution.get("executionTimeMs", 0)) + int(verdict.get("executionTimeMs", 0))
        if not verdict["ok"]:
            execution["status"] = "ERROR"
            execution["passed"] = False
            execution["message"] = verdict["message"]
            return execution
        execution["passed"] = bool(verdict["passed"])
        execution["message"] = None if execution["passed"] else verdict.get("message") or "Validator returned false."
        return execution

    if expected_output:
        actual = _normalize_output(str(execution.get("output") or ""))
        expected = _normalize_output(expected_output)
        execution["passed"] = actual == expected
        execution["message"] = None if execution["passed"] else "Output does not match expected value."
        return execution

    execution["passed"] = False
    execution["message"] = "Test must define expectedOutput or validatorCode."
    return execution


def _run_payload(payload: Dict[str, Any]) -> Dict[str, Any]:
    run_id = str(payload.get("runId", "") or "").strip() or None
    source_code = str(payload.get("sourceCode", "")).strip()
    if not source_code:
        raise ValueError("sourceCode is required.")
    language = str(payload.get("language", "kotlin")).strip().lower() or "kotlin"

    tests = payload.get("tests")
    if not isinstance(tests, list) or not tests:
        raise ValueError("tests must be a non-empty array.")
    if len(tests) > MAX_TESTS:
        raise ValueError(f"Too many tests. Maximum is {MAX_TESTS}.")

    normalized_tests: List[Dict[str, Any]] = []
    for test in tests:
        if not isinstance(test, dict):
            raise ValueError("Each test must be an object.")
        if "id" not in test or "order" not in test:
            raise ValueError("Each test must include id and order.")

        input_data = str(test.get("inputData", ""))
        expected_output = str(test.get("expectedOutput", ""))
        validator_code = str(test.get("validatorCode", ""))
        validator_language = str(test.get("validatorLanguage", "kotlin")).strip().lower() or "kotlin"
        if len(input_data) > MAX_INPUT_CHARS:
            raise ValueError(f"Test inputData is too large. Max {MAX_INPUT_CHARS} characters.")
        if len(expected_output) > MAX_EXPECTED_OUTPUT_CHARS:
            raise ValueError(f"Test expectedOutput is too large. Max {MAX_EXPECTED_OUTPUT_CHARS} characters.")
        if len(validator_code) > MAX_VALIDATOR_CODE_CHARS:
            raise ValueError(f"Test validatorCode is too large. Max {MAX_VALIDATOR_CODE_CHARS} characters.")

        normalized_tests.append(
            {
                "id": int(test["id"]),
                "order": int(test["order"]),
                "inputData": input_data,
                "expectedOutput": expected_output,
                "validatorCode": validator_code,
                "validatorLanguage": validator_language,
                "timeoutMs": _normalize_timeout_ms(test.get("timeoutMs", 1000)),
                "memoryLimitMb": _normalize_memory_limit_mb(test.get("memoryLimitMb", 256)),
            }
        )

    run_hash = _compute_run_hash({"sourceCode": source_code, "language": language, "tests": normalized_tests})
    run_control = _register_run(run_id) if run_id is not None else None
    try:
        if run_control is not None:
            run_control.throw_if_cancelled()
        with tempfile.TemporaryDirectory(prefix="sandbox_run_") as tmp:
            workdir = Path(tmp)
            compiled = _compile_solution(workdir, source_code, language, run_control=run_control)
            suite_execution_time_ms = 0
            if not compiled["ok"]:
                results = [
                    {
                        "id": test["id"],
                        "order": test["order"],
                        "status": "ERROR",
                        "output": None,
                        "passed": False,
                        "executionTimeMs": 0,
                        "memoryUsedKb": None,
                        "message": compiled["message"],
                    }
                    for test in normalized_tests
                ]
            else:
                validator_jars_by_key: Dict[str, str] = {}
                validator_compile_errors: Dict[str, str] = {}
                unique_validators = {}
                for test in normalized_tests:
                    validator_code = test["validatorCode"]
                    if not validator_code:
                        continue
                    validator_language = test["validatorLanguage"]
                    validator_hash = hashlib.sha256(validator_code.encode("utf-8")).hexdigest()
                    key = f"{validator_language}:{validator_hash}"
                    if key in unique_validators:
                        continue
                    unique_validators[key] = (validator_code, validator_language, validator_hash)

                for key, (validator_code, validator_language, validator_hash) in unique_validators.items():
                    if run_control is not None:
                        run_control.throw_if_cancelled()
                    compiled_validator = _compile_validator(
                        workdir=workdir,
                        validator_code=validator_code,
                        validator_language=validator_language,
                        validator_hash=validator_hash,
                        run_control=run_control,
                    )
                    if compiled_validator["ok"]:
                        validator_jars_by_key[key] = str(compiled_validator["jar_path"])
                    else:
                        validator_compile_errors[key] = str(compiled_validator["message"])

                suite_started = time.perf_counter()
                raw_results = [
                    _execute_test(
                        command=list(compiled["command"]),
                        test=test,
                        workdir=str(workdir),
                        run_control=run_control,
                    )
                    for test in normalized_tests
                ]
                suite_execution_time_ms = int((time.perf_counter() - suite_started) * 1000)
                results = [
                    _judge_test_result(
                        execution=execution,
                        test=test,
                        validator_jars_by_key=validator_jars_by_key,
                        validator_compile_errors=validator_compile_errors,
                        run_control=run_control,
                    )
                    for execution, test in zip(raw_results, normalized_tests, strict=True)
                ]
    finally:
        if run_id is not None and run_control is not None:
            _finish_run(run_id, run_control)

    result_hash = _compute_result_hash(results)
    executed_at = datetime.now(timezone.utc).isoformat()
    attestation_payload_hash = _compute_attestation_payload_hash(
        NODE_ID,
        IMAGE_HASH,
        run_hash,
        result_hash,
        executed_at,
    )
    attestation_signature = _sign_attestation(attestation_payload_hash)

    return {
        "nodeId": NODE_ID,
        "imageHash": IMAGE_HASH,
        "runHash": run_hash,
        "resultHash": result_hash,
        "suiteExecutionTimeMs": suite_execution_time_ms,
        "executedAt": executed_at,
        "attestationPayloadHash": attestation_payload_hash,
        "attestationSignature": attestation_signature,
        "attestationScheme": "hmac-sha256",
        "results": results,
    }


class SandboxHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/health":
            _json_response(self, 200, {"status": "ok", "nodeId": NODE_ID})
            return
        if self.path == "/attestation":
            _json_response(
                self,
                200,
                {
                    "nodeId": NODE_ID,
                    "imageHash": IMAGE_HASH,
                    "sandboxVersion": SANDBOX_VERSION,
                    "runnerFileHash": RUNNER_FILE_HASH,
                },
            )
            return
        _json_response(self, 404, {"message": "Not found."})

    def do_POST(self) -> None:  # noqa: N802
        if self.path == "/cancel":
            content_length = self.headers.get("Content-Length")
            try:
                length = int(content_length or "0")
            except ValueError:
                length = 0
            if length <= 0 or length > MAX_BODY_BYTES:
                _json_response(self, 400, {"message": "Invalid request size."})
                return
            raw = self.rfile.read(length)
            try:
                payload = json.loads(raw.decode("utf-8"))
            except ValueError as exc:
                _json_response(self, 400, {"message": str(exc)})
                return
            run_id = str(payload.get("runId", "") or "").strip()
            if not run_id:
                _json_response(self, 400, {"message": "runId is required."})
                return
            _json_response(self, 202, {"cancelled": _cancel_run(run_id)})
            return
        if self.path != "/run":
            _json_response(self, 404, {"message": "Not found."})
            return

        content_length = self.headers.get("Content-Length")
        try:
            length = int(content_length or "0")
        except ValueError:
            length = 0

        if length <= 0 or length > MAX_BODY_BYTES:
            _json_response(self, 400, {"message": "Invalid request size."})
            return

        raw = self.rfile.read(length)
        try:
            payload = json.loads(raw.decode("utf-8"))
            result = _run_payload(payload)
        except ValueError as exc:
            _json_response(self, 400, {"message": str(exc)})
            return
        except RunCancelled as exc:
            _json_response(self, 409, {"message": str(exc)})
            return
        except Exception as exc:
            _json_response(self, 500, {"message": f"Sandbox execution error: {exc}"})
            return

        _json_response(self, 200, result)

    def log_message(self, format: str, *args: Any) -> None:
        return


def main() -> None:
    server = ThreadingHTTPServer(("0.0.0.0", PORT), SandboxHandler)
    server.serve_forever()


if __name__ == "__main__":
    main()
