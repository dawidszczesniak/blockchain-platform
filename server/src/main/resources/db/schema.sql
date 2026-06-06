-- 3NF schema:
-- 1) users: identities and account activity timestamps.
-- 2) problems: core problem attributes and owner relation.
-- 3) problem_participants: many-to-many registrations.
-- 4) problem_tests: ordered test definitions per problem.
-- 5) problem_submissions: attempt history (many submissions per user and problem).
-- 6) problem_submission_test_results: per-test verdicts for a single submission.
-- 7) problem_winners: single winner settlement record per problem.

CREATE TABLE IF NOT EXISTS users (
    user_id BIGSERIAL PRIMARY KEY,
    wallet_address VARCHAR(66) NOT NULL UNIQUE,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS problems (
    problem_id BIGSERIAL PRIMARY KEY,
    created_by_user_id BIGINT NOT NULL REFERENCES users(user_id),
    problem_status VARCHAR(16) NOT NULL DEFAULT 'open'
        CHECK (problem_status IN ('open', 'closed')),
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    constraints_text TEXT NOT NULL DEFAULT '',
    examples_json TEXT NOT NULL DEFAULT '[]',
    reference_solution_code TEXT NOT NULL DEFAULT '',
    reference_solution_hash VARCHAR(66) NOT NULL DEFAULT '0x',
    reference_runtime_ms INTEGER CHECK (reference_runtime_ms >= 0),
    reference_memory_used_kb INTEGER CHECK (reference_memory_used_kb >= 0),
    reference_consensus_nodes INTEGER CHECK (reference_consensus_nodes >= 0),
    validation_node_id VARCHAR(128),
    validation_run_hash VARCHAR(66),
    validation_result_hash VARCHAR(66),
    validation_image_hash VARCHAR(128),
    validated_at TIMESTAMPTZ,
    payment_asset_code VARCHAR(32) NOT NULL DEFAULT 'ETH',
    prize_amount TEXT NOT NULL,
    entry_fee_amount TEXT NOT NULL,
    required_participants INTEGER NOT NULL CHECK (required_participants > 0),
    onchain_competition_id BIGINT UNIQUE,
    onchain_creation_key VARCHAR(66),
    onchain_contract_address VARCHAR(66),
    onchain_creation_tx_hash VARCHAR(128) UNIQUE,
    onchain_creation_from_wallet VARCHAR(66),
    onchain_creation_confirmed_at TIMESTAMPTZ,
    onchain_settlement_status VARCHAR(16) NOT NULL DEFAULT 'disabled'
        CHECK (onchain_settlement_status IN ('pending', 'settled', 'cancelled', 'failed', 'disabled')),
    onchain_settlement_tx_hash VARCHAR(128),
    onchain_settlement_from_wallet VARCHAR(66),
    onchain_settlement_error TEXT,
    onchain_settled_at TIMESTAMPTZ,
    join_until_date DATE NOT NULL,
    submit_until_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (submit_until_date >= join_until_date)
);

CREATE TABLE IF NOT EXISTS problem_participants (
    problem_id BIGINT NOT NULL REFERENCES problems(problem_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    join_tx_hash VARCHAR(128),
    join_from_wallet VARCHAR(66),
    joined_onchain_at TIMESTAMPTZ,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (problem_id, user_id)
);

CREATE TABLE IF NOT EXISTS problem_tests (
    problem_test_id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL REFERENCES problems(problem_id) ON DELETE CASCADE,
    test_order INTEGER NOT NULL CHECK (test_order > 0),
    input_data TEXT NOT NULL DEFAULT '',
    expected_output TEXT NOT NULL DEFAULT '',
    validator_code TEXT NOT NULL,
    validator_language VARCHAR(32) NOT NULL DEFAULT 'kotlin',
    is_hidden BOOLEAN NOT NULL DEFAULT TRUE,
    timeout_ms INTEGER NOT NULL DEFAULT 1000 CHECK (timeout_ms > 0),
    memory_limit_mb INTEGER NOT NULL DEFAULT 256 CHECK (memory_limit_mb > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_problem_tests_problem_id_problem_test_id
        UNIQUE (problem_id, problem_test_id)
);

CREATE TABLE IF NOT EXISTS problem_submissions (
    submission_id BIGSERIAL PRIMARY KEY,
    onchain_submission_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('accepted', 'rejected', 'error')),
    source_code TEXT NOT NULL,
    language VARCHAR(32) NOT NULL,
    code_hash VARCHAR(66) NOT NULL,
    challenge_hash VARCHAR(66) NOT NULL,
    result_hash VARCHAR(66) NOT NULL,
    consensus_image_hash VARCHAR(128),
    consensus_nodes INTEGER NOT NULL CHECK (consensus_nodes >= 0),
    commitment_hash VARCHAR(66) NOT NULL,
    runtime_ms INTEGER NOT NULL DEFAULT 0 CHECK (runtime_ms >= 0),
    memory_used_kb INTEGER CHECK (memory_used_kb >= 0),
    onchain_record_contract_address VARCHAR(66),
    onchain_record_tx_hash VARCHAR(128),
    onchain_record_from_wallet VARCHAR(66),
    onchain_record_error TEXT,
    onchain_recorded_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_problem_submissions_source_code_length
        CHECK (char_length(source_code) <= 120000),
    CONSTRAINT uq_problem_submissions_submission_problem
        UNIQUE (submission_id, problem_id),
    CONSTRAINT uq_problem_submissions_submission_problem_user
        UNIQUE (submission_id, problem_id, user_id),
    FOREIGN KEY (problem_id, user_id)
        REFERENCES problem_participants(problem_id, user_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS problem_submission_test_results (
    submission_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    problem_test_id BIGINT NOT NULL,
    result_status VARCHAR(16) NOT NULL
        CHECK (result_status IN ('passed', 'failed', 'error', 'timeout')),
    execution_time_ms INTEGER NOT NULL CHECK (execution_time_ms >= 0),
    memory_used_kb INTEGER CHECK (memory_used_kb >= 0),
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (submission_id, problem_test_id),
    CONSTRAINT fk_problem_submission_test_results_submission_problem
        FOREIGN KEY (submission_id, problem_id)
        REFERENCES problem_submissions(submission_id, problem_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_problem_submission_test_results_problem_test
        FOREIGN KEY (problem_id, problem_test_id)
        REFERENCES problem_tests(problem_id, problem_test_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS problem_submission_judge_jobs (
    job_id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL REFERENCES problems(problem_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    source_code TEXT NOT NULL,
    language VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('queued', 'running', 'accepted', 'rejected', 'error')),
    status_message TEXT,
    result_payload_json TEXT,
    preview_payload_json TEXT,
    submission_id BIGINT REFERENCES problem_submissions(submission_id) ON DELETE SET NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_problem_submission_judge_jobs_source_code_length
        CHECK (char_length(source_code) <= 120000),
    CONSTRAINT fk_problem_submission_judge_jobs_submission_context
        FOREIGN KEY (submission_id, problem_id, user_id)
        REFERENCES problem_submissions(submission_id, problem_id, user_id)
);

CREATE TABLE IF NOT EXISTS competition_settlement_jobs (
    job_id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL REFERENCES problems(problem_id) ON DELETE CASCADE,
    competition_id BIGINT NOT NULL,
    job_type VARCHAR(32) NOT NULL
        CHECK (job_type IN ('registration_deadline', 'submission_deadline')),
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('scheduled', 'running', 'completed', 'dead')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    run_at TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    status_message TEXT,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (problem_id, job_type)
);

CREATE INDEX IF NOT EXISTS idx_competition_settlement_jobs_status_available
    ON competition_settlement_jobs(status, available_at, run_at);

CREATE INDEX IF NOT EXISTS idx_competition_settlement_jobs_problem
    ON competition_settlement_jobs(problem_id);

CREATE TABLE IF NOT EXISTS problem_submission_attestations (
    submission_id BIGINT NOT NULL REFERENCES problem_submissions(submission_id) ON DELETE CASCADE,
    node_id VARCHAR(128) NOT NULL,
    node_url TEXT NOT NULL,
    image_hash VARCHAR(128),
    run_hash VARCHAR(66),
    result_hash VARCHAR(66),
    attestation_payload_hash VARCHAR(66),
    attestation_signature VARCHAR(256),
    attestation_scheme VARCHAR(32) NOT NULL DEFAULT 'hmac-sha256',
    is_valid BOOLEAN NOT NULL DEFAULT FALSE,
    is_consensus BOOLEAN NOT NULL DEFAULT FALSE,
    node_status VARCHAR(16) NOT NULL
        CHECK (node_status IN ('ok', 'error', 'invalid')),
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (submission_id, node_id)
);

CREATE TABLE IF NOT EXISTS problem_winners (
    problem_id BIGINT NOT NULL,
    winner_user_id BIGINT NOT NULL,
    payout_amount TEXT NOT NULL,
    settlement_tx_hash VARCHAR(128),
    settlement_from_wallet VARCHAR(66),
    won_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (problem_id, winner_user_id),
    CONSTRAINT uq_problem_winners_problem_id UNIQUE (problem_id),
    FOREIGN KEY (problem_id, winner_user_id)
        REFERENCES problem_participants(problem_id, user_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dashboard_daily_metrics (
    metric_date DATE PRIMARY KEY,
    active_challenges INTEGER NOT NULL CHECK (active_challenges >= 0),
    completed_challenges INTEGER NOT NULL DEFAULT 0 CHECK (completed_challenges >= 0),
    prize_pool_amount TEXT NOT NULL DEFAULT 'Mixed assets'
);

ALTER TABLE problems
    DROP CONSTRAINT IF EXISTS problems_prize_amount_check;

ALTER TABLE problems
    DROP CONSTRAINT IF EXISTS problems_entry_fee_amount_check;

ALTER TABLE problem_winners
    DROP CONSTRAINT IF EXISTS problem_winners_payout_amount_check;

ALTER TABLE dashboard_daily_metrics
    DROP CONSTRAINT IF EXISTS dashboard_daily_metrics_prize_pool_amount_check;

ALTER TABLE dashboard_daily_metrics
    ADD COLUMN IF NOT EXISTS completed_challenges INTEGER NOT NULL DEFAULT 0;

ALTER TABLE problems
    ALTER COLUMN prize_amount TYPE TEXT USING prize_amount::TEXT;

ALTER TABLE problems
    ALTER COLUMN entry_fee_amount TYPE TEXT USING entry_fee_amount::TEXT;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS payment_asset_code VARCHAR(32);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS constraints_text TEXT;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS examples_json TEXT;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS reference_solution_code TEXT;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS reference_solution_hash VARCHAR(66);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS reference_runtime_ms INTEGER;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS reference_memory_used_kb INTEGER;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS reference_consensus_nodes INTEGER;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS validation_node_id VARCHAR(128);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS validation_run_hash VARCHAR(66);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS validation_result_hash VARCHAR(66);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS validation_image_hash VARCHAR(128);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS validated_at TIMESTAMPTZ;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS onchain_competition_id BIGINT;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS onchain_creation_key VARCHAR(66);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS onchain_contract_address VARCHAR(66);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS onchain_creation_tx_hash VARCHAR(128);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS onchain_creation_from_wallet VARCHAR(66);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS onchain_creation_confirmed_at TIMESTAMPTZ;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS onchain_settlement_status VARCHAR(16);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS onchain_settlement_tx_hash VARCHAR(128);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS onchain_settlement_from_wallet VARCHAR(66);

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS onchain_settlement_error TEXT;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS onchain_settled_at TIMESTAMPTZ;

ALTER TABLE problem_participants
    ADD COLUMN IF NOT EXISTS join_tx_hash VARCHAR(128);

ALTER TABLE problem_participants
    ADD COLUMN IF NOT EXISTS join_from_wallet VARCHAR(66);

ALTER TABLE problem_participants
    ADD COLUMN IF NOT EXISTS joined_onchain_at TIMESTAMPTZ;

ALTER TABLE problem_winners
    ADD COLUMN IF NOT EXISTS settlement_tx_hash VARCHAR(128);

ALTER TABLE problem_winners
    ADD COLUMN IF NOT EXISTS settlement_from_wallet VARCHAR(66);

ALTER TABLE problem_winners
    ALTER COLUMN payout_amount TYPE TEXT USING payout_amount::TEXT;

ALTER TABLE dashboard_daily_metrics
    ALTER COLUMN prize_pool_amount TYPE TEXT USING prize_pool_amount::TEXT;

UPDATE problems
SET constraints_text = COALESCE(constraints_text, '')
WHERE constraints_text IS NULL;

UPDATE problems
SET examples_json = COALESCE(NULLIF(examples_json, ''), '[]')
WHERE examples_json IS NULL OR examples_json = '';

UPDATE problems
SET reference_solution_code = COALESCE(reference_solution_code, '')
WHERE reference_solution_code IS NULL;

UPDATE problems
SET reference_solution_hash = COALESCE(NULLIF(reference_solution_hash, ''), '0x')
WHERE reference_solution_hash IS NULL OR reference_solution_hash = '';

UPDATE problems
SET onchain_settlement_status = 'disabled'
WHERE onchain_settlement_status IS NULL OR onchain_settlement_status = '';

UPDATE problems
SET payment_asset_code = 'ETH'
WHERE payment_asset_code IS NULL OR payment_asset_code = '';

UPDATE problems p
SET onchain_creation_from_wallet = u.wallet_address
FROM users u
WHERE p.created_by_user_id = u.user_id
  AND p.onchain_creation_from_wallet IS NULL;

UPDATE problem_participants pp
SET join_from_wallet = u.wallet_address
FROM users u
WHERE pp.user_id = u.user_id
  AND pp.join_from_wallet IS NULL;

INSERT INTO competition_settlement_jobs (
    problem_id,
    competition_id,
    job_type,
    status,
    attempts,
    run_at,
    available_at,
    created_at
)
SELECT
    p.problem_id,
    p.onchain_competition_id,
    'registration_deadline',
    'scheduled',
    0,
    ((p.join_until_date + 1)::timestamp AT TIME ZONE 'UTC'),
    ((p.join_until_date + 1)::timestamp AT TIME ZONE 'UTC'),
    COALESCE(p.onchain_creation_confirmed_at, p.created_at, NOW())
FROM problems p
WHERE p.onchain_competition_id IS NOT NULL
  AND p.onchain_settlement_status = 'pending'
ON CONFLICT (problem_id, job_type) DO NOTHING;

INSERT INTO competition_settlement_jobs (
    problem_id,
    competition_id,
    job_type,
    status,
    attempts,
    run_at,
    available_at,
    created_at
)
SELECT
    p.problem_id,
    p.onchain_competition_id,
    'submission_deadline',
    'scheduled',
    0,
    ((p.submit_until_date + 1)::timestamp AT TIME ZONE 'UTC'),
    ((p.submit_until_date + 1)::timestamp AT TIME ZONE 'UTC'),
    COALESCE(p.onchain_creation_confirmed_at, p.created_at, NOW())
FROM problems p
WHERE p.onchain_competition_id IS NOT NULL
  AND p.onchain_settlement_status = 'pending'
ON CONFLICT (problem_id, job_type) DO NOTHING;

ALTER TABLE problems
    ALTER COLUMN constraints_text SET DEFAULT '';

ALTER TABLE problems
    ALTER COLUMN examples_json SET DEFAULT '[]';

ALTER TABLE problems
    ALTER COLUMN reference_solution_code SET DEFAULT '';

ALTER TABLE problems
    ALTER COLUMN onchain_settlement_status SET DEFAULT 'disabled';

ALTER TABLE problems
    ALTER COLUMN payment_asset_code SET DEFAULT 'ETH';

ALTER TABLE problems
    ALTER COLUMN onchain_settlement_status SET NOT NULL;

ALTER TABLE problems
    ALTER COLUMN payment_asset_code SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS problems_onchain_competition_id_uq
    ON problems(onchain_competition_id)
    WHERE onchain_competition_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS problems_onchain_creation_tx_hash_uq
    ON problems(onchain_creation_tx_hash)
    WHERE onchain_creation_tx_hash IS NOT NULL;

ALTER TABLE problems
    ALTER COLUMN reference_solution_hash SET DEFAULT '0x';

ALTER TABLE problems
    ALTER COLUMN constraints_text SET NOT NULL;

ALTER TABLE problems
    ALTER COLUMN examples_json SET NOT NULL;

ALTER TABLE problems
    ALTER COLUMN reference_solution_code SET NOT NULL;

ALTER TABLE problems
    ALTER COLUMN reference_solution_hash SET NOT NULL;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS source_code TEXT;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS language VARCHAR(32);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS onchain_submission_id BIGINT;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS code_hash VARCHAR(66);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'problem_submissions'
          AND column_name = 'tests_hash'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'problem_submissions'
          AND column_name = 'challenge_hash'
    ) THEN
        ALTER TABLE problem_submissions
            RENAME COLUMN tests_hash TO challenge_hash;
    END IF;
END $$;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS challenge_hash VARCHAR(66);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS result_hash VARCHAR(66);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS consensus_image_hash VARCHAR(128);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'problem_submissions'
          AND column_name = 'onchain_result_registry_address'
    ) THEN
        ALTER TABLE problem_submissions
            RENAME COLUMN onchain_result_registry_address TO onchain_record_contract_address;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'problem_submissions'
          AND column_name = 'onchain_result_tx_hash'
    ) THEN
        ALTER TABLE problem_submissions
            RENAME COLUMN onchain_result_tx_hash TO onchain_record_tx_hash;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'problem_submissions'
          AND column_name = 'onchain_result_error'
    ) THEN
        ALTER TABLE problem_submissions
            RENAME COLUMN onchain_result_error TO onchain_record_error;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'problem_submissions'
          AND column_name = 'onchain_result_recorded_at'
    ) THEN
        ALTER TABLE problem_submissions
            RENAME COLUMN onchain_result_recorded_at TO onchain_recorded_at;
    END IF;
END $$;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS consensus_nodes INTEGER;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS commitment_hash VARCHAR(66);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS runtime_ms INTEGER;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS memory_used_kb INTEGER;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS onchain_record_contract_address VARCHAR(66);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS onchain_record_tx_hash VARCHAR(128);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS onchain_record_from_wallet VARCHAR(66);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS onchain_record_error TEXT;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS onchain_recorded_at TIMESTAMPTZ;

UPDATE problem_submissions
SET source_code = COALESCE(source_code, '')
WHERE source_code IS NULL;

UPDATE problem_submissions
SET onchain_submission_id = (
    (FLOOR(EXTRACT(EPOCH FROM submitted_at) * 1000))::BIGINT * 1048576
    + MOD(submission_id, 1048576)
)
WHERE onchain_submission_id IS NULL;

UPDATE problem_submissions
SET language = COALESCE(NULLIF(language, ''), 'unknown')
WHERE language IS NULL OR language = '';

UPDATE problem_submissions
SET code_hash = COALESCE(NULLIF(code_hash, ''), '0x')
WHERE code_hash IS NULL OR code_hash = '';

UPDATE problem_submissions
SET challenge_hash = COALESCE(NULLIF(challenge_hash, ''), '0x')
WHERE challenge_hash IS NULL OR challenge_hash = '';

UPDATE problem_submissions
SET result_hash = COALESCE(NULLIF(result_hash, ''), '0x')
WHERE result_hash IS NULL OR result_hash = '';

UPDATE problem_submissions
SET consensus_nodes = COALESCE(consensus_nodes, 0)
WHERE consensus_nodes IS NULL;

UPDATE problem_submissions
SET commitment_hash = COALESCE(NULLIF(commitment_hash, ''), '0x')
WHERE commitment_hash IS NULL OR commitment_hash = '';

UPDATE problem_submissions
SET runtime_ms = COALESCE((
    SELECT MAX(problem_submission_test_results.execution_time_ms)
    FROM problem_submission_test_results
    WHERE problem_submission_test_results.submission_id = problem_submissions.submission_id
), 0)
WHERE runtime_ms IS NULL;

UPDATE problem_submissions
SET memory_used_kb = (
    SELECT MAX(problem_submission_test_results.memory_used_kb)
    FROM problem_submission_test_results
    WHERE problem_submission_test_results.submission_id = problem_submissions.submission_id
)
WHERE memory_used_kb IS NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN source_code SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN onchain_submission_id SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN language SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN code_hash SET DEFAULT '0x';

ALTER TABLE problem_submissions
    ALTER COLUMN challenge_hash SET DEFAULT '0x';

ALTER TABLE problem_submissions
    ALTER COLUMN result_hash SET DEFAULT '0x';

ALTER TABLE problem_submissions
    ALTER COLUMN consensus_nodes SET DEFAULT 0;

ALTER TABLE problem_submissions
    ALTER COLUMN commitment_hash SET DEFAULT '0x';

ALTER TABLE problem_submissions
    ALTER COLUMN runtime_ms SET DEFAULT 0;

ALTER TABLE problem_submissions
    ALTER COLUMN code_hash SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN challenge_hash SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN result_hash SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN consensus_nodes SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN commitment_hash SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN runtime_ms SET NOT NULL;

DO $$
BEGIN
    ALTER TABLE problems
        ADD CONSTRAINT chk_problems_prize_amount_atomic_format
        CHECK (prize_amount ~ '^[0-9]+$');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE problems
        ADD CONSTRAINT chk_problems_entry_fee_amount_atomic_format
        CHECK (entry_fee_amount ~ '^[0-9]+$');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE problem_winners
        ADD CONSTRAINT chk_problem_winners_payout_amount_atomic_format
        CHECK (payout_amount ~ '^[0-9]+$');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE problem_submissions
        ADD CONSTRAINT chk_problem_submissions_consensus_nodes_non_negative
        CHECK (consensus_nodes >= 0);
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE problem_submissions
        ADD CONSTRAINT chk_problem_submissions_runtime_ms_non_negative
        CHECK (runtime_ms >= 0);
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE problem_submissions
        ADD CONSTRAINT chk_problem_submissions_memory_used_kb_non_negative
        CHECK (memory_used_kb IS NULL OR memory_used_kb >= 0);
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE problem_submissions
        ADD CONSTRAINT chk_problem_submissions_onchain_submission_id_positive
        CHECK (onchain_submission_id > 0);
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE problem_tests
    ADD COLUMN IF NOT EXISTS input_data TEXT;

ALTER TABLE problem_tests
    ADD COLUMN IF NOT EXISTS expected_output TEXT;

ALTER TABLE problem_tests
    ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN;

ALTER TABLE problem_tests
    ADD COLUMN IF NOT EXISTS timeout_ms INTEGER;

ALTER TABLE problem_tests
    ADD COLUMN IF NOT EXISTS memory_limit_mb INTEGER;

ALTER TABLE problem_tests
    ADD COLUMN IF NOT EXISTS validator_language VARCHAR(32);

UPDATE problem_tests
SET input_data = COALESCE(input_data, '')
WHERE input_data IS NULL;

UPDATE problem_tests
SET expected_output = COALESCE(expected_output, '')
WHERE expected_output IS NULL;

UPDATE problem_tests
SET is_hidden = COALESCE(is_hidden, TRUE)
WHERE is_hidden IS NULL;

UPDATE problem_tests
SET timeout_ms = COALESCE(timeout_ms, 1000)
WHERE timeout_ms IS NULL OR timeout_ms <= 0;

UPDATE problem_tests
SET memory_limit_mb = COALESCE(memory_limit_mb, 256)
WHERE memory_limit_mb IS NULL OR memory_limit_mb <= 0;

UPDATE problem_tests
SET validator_language = COALESCE(NULLIF(validator_language, ''), 'kotlin')
WHERE validator_language IS NULL OR validator_language = '';

ALTER TABLE problem_tests
    ALTER COLUMN input_data SET DEFAULT '';

ALTER TABLE problem_tests
    ALTER COLUMN expected_output SET DEFAULT '';

ALTER TABLE problem_tests
    ALTER COLUMN is_hidden SET DEFAULT TRUE;

ALTER TABLE problem_tests
    ALTER COLUMN timeout_ms SET DEFAULT 1000;

ALTER TABLE problem_tests
    ALTER COLUMN memory_limit_mb SET DEFAULT 256;

ALTER TABLE problem_tests
    ALTER COLUMN validator_language SET DEFAULT 'kotlin';

ALTER TABLE problem_tests
    ALTER COLUMN input_data SET NOT NULL;

ALTER TABLE problem_tests
    ALTER COLUMN expected_output SET NOT NULL;

ALTER TABLE problem_tests
    ALTER COLUMN is_hidden SET NOT NULL;

ALTER TABLE problem_tests
    ALTER COLUMN timeout_ms SET NOT NULL;

ALTER TABLE problem_tests
    ALTER COLUMN memory_limit_mb SET NOT NULL;

ALTER TABLE problem_tests
    ALTER COLUMN validator_language SET NOT NULL;

DO $$
BEGIN
    ALTER TABLE problem_tests
        ADD CONSTRAINT chk_problem_tests_timeout_ms_positive CHECK (timeout_ms > 0);
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE problem_tests
        ADD CONSTRAINT chk_problem_tests_memory_limit_mb_positive CHECK (memory_limit_mb > 0);
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'problem_tests'::regclass
          AND conname = 'uq_problem_tests_problem_id_problem_test_id'
    ) THEN
        ALTER TABLE problem_tests
            ADD CONSTRAINT uq_problem_tests_problem_id_problem_test_id
            UNIQUE (problem_id, problem_test_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'problem_submissions'::regclass
          AND conname = 'uq_problem_submissions_submission_problem'
    ) THEN
        ALTER TABLE problem_submissions
            ADD CONSTRAINT uq_problem_submissions_submission_problem
            UNIQUE (submission_id, problem_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'problem_submissions'::regclass
          AND conname = 'uq_problem_submissions_submission_problem_user'
    ) THEN
        ALTER TABLE problem_submissions
            ADD CONSTRAINT uq_problem_submissions_submission_problem_user
            UNIQUE (submission_id, problem_id, user_id);
    END IF;
END $$;

ALTER TABLE problem_submission_test_results
    ADD COLUMN IF NOT EXISTS problem_id BIGINT;

UPDATE problem_submission_test_results r
SET problem_id = s.problem_id
FROM problem_submissions s
WHERE r.submission_id = s.submission_id
  AND r.problem_id IS NULL;

ALTER TABLE problem_submission_test_results
    ALTER COLUMN problem_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'problem_submission_test_results'::regclass
          AND conname = 'fk_problem_submission_test_results_submission_problem'
    ) THEN
        ALTER TABLE problem_submission_test_results
            ADD CONSTRAINT fk_problem_submission_test_results_submission_problem
            FOREIGN KEY (submission_id, problem_id)
            REFERENCES problem_submissions(submission_id, problem_id)
            ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'problem_submission_test_results'::regclass
          AND conname = 'fk_problem_submission_test_results_problem_test'
    ) THEN
        ALTER TABLE problem_submission_test_results
            ADD CONSTRAINT fk_problem_submission_test_results_problem_test
            FOREIGN KEY (problem_id, problem_test_id)
            REFERENCES problem_tests(problem_id, problem_test_id)
            ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'problem_submission_judge_jobs'::regclass
          AND conname = 'fk_problem_submission_judge_jobs_submission_context'
    ) THEN
        ALTER TABLE problem_submission_judge_jobs
            ADD CONSTRAINT fk_problem_submission_judge_jobs_submission_context
            FOREIGN KEY (submission_id, problem_id, user_id)
            REFERENCES problem_submissions(submission_id, problem_id, user_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'problem_winners'::regclass
          AND conname = 'uq_problem_winners_problem_id'
    ) THEN
        ALTER TABLE problem_winners
            ADD CONSTRAINT uq_problem_winners_problem_id
            UNIQUE (problem_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'problem_submission_judge_jobs'::regclass
          AND conname = 'chk_problem_submission_judge_jobs_source_code_length'
    ) THEN
        ALTER TABLE problem_submission_judge_jobs
            ADD CONSTRAINT chk_problem_submission_judge_jobs_source_code_length
            CHECK (char_length(source_code) <= 120000);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'problem_submissions'::regclass
          AND conname = 'chk_problem_submissions_source_code_length'
    ) THEN
        ALTER TABLE problem_submissions
            ADD CONSTRAINT chk_problem_submissions_source_code_length
            CHECK (char_length(source_code) <= 120000);
    END IF;
END $$;

ALTER TABLE dashboard_daily_metrics
    DROP COLUMN IF EXISTS created_at;

ALTER TABLE dashboard_daily_metrics
    DROP COLUMN IF EXISTS submissions_count;

DROP TABLE IF EXISTS website_updates;

DROP INDEX IF EXISTS idx_problem_participants_problem_id;

CREATE INDEX IF NOT EXISTS idx_problem_participants_user_id
    ON problem_participants(user_id);

CREATE INDEX IF NOT EXISTS idx_problem_tests_problem_id
    ON problem_tests(problem_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_problem_tests_problem_id_test_order
    ON problem_tests(problem_id, test_order);

CREATE INDEX IF NOT EXISTS idx_problem_submission_test_results_problem_test_id
    ON problem_submission_test_results(problem_test_id);

CREATE INDEX IF NOT EXISTS idx_problem_submission_test_results_submission_problem
    ON problem_submission_test_results(submission_id, problem_id);

CREATE INDEX IF NOT EXISTS idx_problem_submission_test_results_problem_test
    ON problem_submission_test_results(problem_id, problem_test_id);

CREATE INDEX IF NOT EXISTS idx_problem_submissions_user_id
    ON problem_submissions(user_id);

CREATE INDEX IF NOT EXISTS idx_problem_submissions_problem_user
    ON problem_submissions(problem_id, user_id);

DROP INDEX IF EXISTS idx_problem_submissions_onchain_record_tx;

CREATE UNIQUE INDEX IF NOT EXISTS idx_problem_submissions_onchain_record_tx_uq
    ON problem_submissions(onchain_record_tx_hash)
    WHERE onchain_record_tx_hash IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_problem_submissions_onchain_submission_id
    ON problem_submissions(onchain_submission_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_problem_participants_join_tx_hash_uq
    ON problem_participants(join_tx_hash)
    WHERE join_tx_hash IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_problems_onchain_settlement_tx_hash_uq
    ON problems(onchain_settlement_tx_hash)
    WHERE onchain_settlement_tx_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_problem_submission_judge_jobs_status_requested_at
    ON problem_submission_judge_jobs(status, requested_at);

CREATE INDEX IF NOT EXISTS idx_problem_submission_judge_jobs_user_id
    ON problem_submission_judge_jobs(user_id, requested_at);

CREATE INDEX IF NOT EXISTS idx_problem_submission_judge_jobs_submission_context
    ON problem_submission_judge_jobs(submission_id, problem_id, user_id)
    WHERE submission_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_problem_submission_attestations_submission_id
    ON problem_submission_attestations(submission_id);

CREATE INDEX IF NOT EXISTS idx_problem_submission_attestations_node_id
    ON problem_submission_attestations(node_id);

CREATE INDEX IF NOT EXISTS idx_problems_created_by_user_id
    ON problems(created_by_user_id);

CREATE INDEX IF NOT EXISTS idx_problems_problem_status
    ON problems(problem_status);

CREATE INDEX IF NOT EXISTS idx_problem_winners_winner_user_id
    ON problem_winners(winner_user_id);

CREATE INDEX IF NOT EXISTS idx_dashboard_daily_metrics_metric_date
    ON dashboard_daily_metrics(metric_date DESC);
