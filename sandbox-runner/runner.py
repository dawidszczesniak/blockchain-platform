#!/usr/bin/env python3
import base64
import hashlib
import hmac
import json
import os
import subprocess
import tempfile
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
    canonical = "\n".join(
        f"{int(item.get('id', 0))}|{int(item.get('order', 0))}|{str(item.get('status', ''))}|"
        f"{str(item.get('output', '') or '')}|{str(bool(item.get('passed', False))).lower()}|"
        f"{int(item.get('executionTimeMs', 0))}|{str(item.get('message', '') or '')}"
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


def _compile_solution(workdir: Path, source_code: str) -> Dict[str, Any]:
    source_path = workdir / "Solution.kt"
    harness_path = workdir / "Harness.kt"
    classes_path = workdir / "classes"
    source_path.write_text(source_code, encoding="utf-8")
    harness_path.write_text(HARNESS_CODE, encoding="utf-8")
    classes_path.mkdir(parents=True, exist_ok=True)

    process = subprocess.run(
        ["kotlinc", str(source_path), str(harness_path), "-d", str(classes_path)],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd=str(workdir),
    )
    if process.returncode != 0:
        message = (process.stderr or process.stdout or "Compilation failed.").strip()
        return {"ok": False, "message": message}
    return {"ok": True, "classes_path": str(classes_path), "entrypoint": "HarnessKt"}


def _compile_validator(workdir: Path, validator_code: str, validator_language: str, validator_hash: str) -> Dict[str, Any]:
    if validator_language.lower() != "kotlin":
        return {"ok": False, "message": f"Unsupported validator language '{validator_language}'."}

    validator_source = workdir / f"Validator_{validator_hash}.kt"
    validator_harness = workdir / f"ValidatorHarness_{validator_hash}.kt"
    validator_jar = workdir / f"validator_{validator_hash}.jar"
    validator_source.write_text(validator_code, encoding="utf-8")
    validator_harness.write_text(VALIDATOR_HARNESS_CODE, encoding="utf-8")

    process = subprocess.run(
        ["kotlinc", str(validator_source), str(validator_harness), "-include-runtime", "-d", str(validator_jar)],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd=str(workdir),
    )
    if process.returncode != 0:
        message = (process.stderr or process.stdout or "Validator compilation failed.").strip()
        return {"ok": False, "message": message}
    return {"ok": True, "jar_path": str(validator_jar)}


def _execute_test(classes_path: str, entrypoint: str, test: Dict[str, Any]) -> Dict[str, Any]:
    timeout_ms = _normalize_timeout_ms(test.get("timeoutMs"))
    memory_limit_mb = _normalize_memory_limit_mb(test.get("memoryLimitMb"))
    started = time.perf_counter()
    try:
        result = subprocess.run(
            ["kotlin", f"-J-Xmx{memory_limit_mb}m", "-classpath", classes_path, entrypoint],
            input=str(test.get("inputData", "")),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=timeout_ms / 1000.0,
        )
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        if result.returncode != 0:
            return {
                "id": int(test["id"]),
                "order": int(test["order"]),
                "status": "ERROR",
                "output": None,
                "passed": False,
                "executionTimeMs": elapsed_ms,
                "message": (result.stderr or result.stdout or f"Process exited with code {result.returncode}").strip(),
            }
        return {
            "id": int(test["id"]),
            "order": int(test["order"]),
            "status": "OK",
            "output": result.stdout,
            "passed": False,
            "executionTimeMs": elapsed_ms,
            "message": None,
        }
    except subprocess.TimeoutExpired:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return {
            "id": int(test["id"]),
            "order": int(test["order"]),
            "status": "TIMEOUT",
            "output": None,
            "passed": False,
            "executionTimeMs": elapsed_ms,
            "message": "Execution timed out.",
        }
    except Exception as exc:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return {
            "id": int(test["id"]),
            "order": int(test["order"]),
            "status": "ERROR",
            "output": None,
            "passed": False,
            "executionTimeMs": elapsed_ms,
            "message": str(exc),
        }


def _execute_validator(
    validator_jar_path: str,
    test: Dict[str, Any],
    actual_output: str,
) -> Dict[str, Any]:
    timeout_ms = min(_normalize_timeout_ms(test.get("timeoutMs")), MAX_VALIDATOR_TIMEOUT_MS)
    encoded_input = base64.b64encode(str(test.get("inputData", "")).encode("utf-8")).decode("ascii")
    encoded_expected = base64.b64encode(str(test.get("expectedOutput", "")).encode("utf-8")).decode("ascii")
    encoded_actual = base64.b64encode(actual_output.encode("utf-8")).decode("ascii")
    payload = f"{encoded_input}\n{encoded_expected}\n{encoded_actual}\n"
    started = time.perf_counter()
    try:
        result = subprocess.run(
            ["java", "-jar", validator_jar_path],
            input=payload,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=timeout_ms / 1000.0,
        )
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        if result.returncode != 0:
            message = (result.stderr or result.stdout or "Validator execution failed.").strip()
            return {"ok": False, "passed": False, "message": message, "executionTimeMs": elapsed_ms}
        verdict_raw = (result.stdout or "").strip().lower()
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
    except subprocess.TimeoutExpired:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return {"ok": False, "passed": False, "message": "Validator timed out.", "executionTimeMs": elapsed_ms}
    except Exception as exc:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return {"ok": False, "passed": False, "message": str(exc), "executionTimeMs": elapsed_ms}


def _judge_test_result(
    execution: Dict[str, Any],
    test: Dict[str, Any],
    validator_jars_by_key: Dict[str, str],
    validator_compile_errors: Dict[str, str],
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
    source_code = str(payload.get("sourceCode", "")).strip()
    if not source_code:
        raise ValueError("sourceCode is required.")

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

    run_hash = _compute_run_hash({"sourceCode": source_code, "tests": normalized_tests})

    with tempfile.TemporaryDirectory(prefix="sandbox_run_") as tmp:
        workdir = Path(tmp)
        compiled = _compile_solution(workdir, source_code)
        if not compiled["ok"]:
            results = [
                {
                    "id": test["id"],
                    "order": test["order"],
                    "status": "ERROR",
                    "output": None,
                    "passed": False,
                    "executionTimeMs": 0,
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
                compiled_validator = _compile_validator(
                    workdir=workdir,
                    validator_code=validator_code,
                    validator_language=validator_language,
                    validator_hash=validator_hash,
                )
                if compiled_validator["ok"]:
                    validator_jars_by_key[key] = str(compiled_validator["jar_path"])
                else:
                    validator_compile_errors[key] = str(compiled_validator["message"])

            classes_path = str(compiled["classes_path"])
            entrypoint = str(compiled["entrypoint"])
            raw_results = [_execute_test(classes_path, entrypoint, test) for test in normalized_tests]
            results = [
                _judge_test_result(
                    execution=execution,
                    test=test,
                    validator_jars_by_key=validator_jars_by_key,
                    validator_compile_errors=validator_compile_errors,
                )
                for execution, test in zip(raw_results, normalized_tests, strict=True)
            ]

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
