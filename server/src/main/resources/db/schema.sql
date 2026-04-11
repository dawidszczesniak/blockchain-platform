-- 3NF schema:
-- 1) users: identities and account activity timestamps.
-- 2) problems: core problem attributes and owner relation.
-- 3) problem_participants: many-to-many registrations.
-- 4) problem_tests: ordered test definitions per problem.
-- 5) problem_submissions: attempt history (many submissions per user and problem).
-- 6) problem_submission_test_results: per-test verdicts for a single submission.
-- 7) problem_winners: history of user wins per problem.

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
    reference_solution_hash VARCHAR(66) NOT NULL DEFAULT '0x',
    validation_node_id VARCHAR(128),
    validation_run_hash VARCHAR(66),
    validation_result_hash VARCHAR(66),
    validation_image_hash VARCHAR(128),
    validated_at TIMESTAMPTZ,
    prize_amount BIGINT NOT NULL CHECK (prize_amount >= 0),
    entry_fee_amount BIGINT NOT NULL CHECK (entry_fee_amount >= 0),
    required_participants INTEGER NOT NULL CHECK (required_participants > 0),
    join_until_date DATE NOT NULL,
    submit_until_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (submit_until_date >= join_until_date)
);

CREATE TABLE IF NOT EXISTS problem_participants (
    problem_id BIGINT NOT NULL REFERENCES problems(problem_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS problem_submissions (
    submission_id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('accepted', 'rejected', 'error')),
    source_code TEXT NOT NULL,
    language VARCHAR(32) NOT NULL,
    code_hash VARCHAR(66) NOT NULL,
    tests_hash VARCHAR(66) NOT NULL,
    result_hash VARCHAR(66) NOT NULL,
    consensus_image_hash VARCHAR(128),
    consensus_nodes INTEGER NOT NULL CHECK (consensus_nodes >= 0),
    commitment_hash VARCHAR(66) NOT NULL,
    runtime_ms INTEGER NOT NULL DEFAULT 0 CHECK (runtime_ms >= 0),
    anchor_status VARCHAR(16) NOT NULL
        CHECK (anchor_status IN ('pending', 'anchored', 'failed', 'disabled')),
    anchor_batch_id BIGINT,
    anchor_merkle_root VARCHAR(66),
    anchor_merkle_proof_json TEXT NOT NULL DEFAULT '[]',
    anchor_tx_hash VARCHAR(128),
    anchor_error TEXT,
    anchored_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (problem_id, user_id)
        REFERENCES problem_participants(problem_id, user_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS problem_submission_test_results (
    submission_id BIGINT NOT NULL REFERENCES problem_submissions(submission_id) ON DELETE CASCADE,
    problem_test_id BIGINT NOT NULL REFERENCES problem_tests(problem_test_id) ON DELETE CASCADE,
    result_status VARCHAR(16) NOT NULL
        CHECK (result_status IN ('passed', 'failed', 'error', 'timeout')),
    execution_time_ms INTEGER NOT NULL CHECK (execution_time_ms >= 0),
    memory_used_kb INTEGER CHECK (memory_used_kb >= 0),
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (submission_id, problem_test_id)
);

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

CREATE TABLE IF NOT EXISTS submission_anchor_batches (
    batch_id BIGSERIAL PRIMARY KEY,
    merkle_root_hash VARCHAR(66) NOT NULL,
    leaves_count INTEGER NOT NULL CHECK (leaves_count > 0),
    from_submission_id BIGINT NOT NULL,
    to_submission_id BIGINT NOT NULL,
    chain_id BIGINT,
    contract_address VARCHAR(66),
    tx_hash VARCHAR(128),
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('pending', 'anchored', 'failed')),
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    anchored_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS problem_winners (
    problem_id BIGINT NOT NULL,
    winner_user_id BIGINT NOT NULL,
    payout_amount BIGINT NOT NULL CHECK (payout_amount >= 0),
    won_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (problem_id, winner_user_id),
    FOREIGN KEY (problem_id, winner_user_id)
        REFERENCES problem_participants(problem_id, user_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dashboard_daily_metrics (
    metric_date DATE PRIMARY KEY,
    active_challenges INTEGER NOT NULL CHECK (active_challenges >= 0),
    prize_pool_amount BIGINT NOT NULL CHECK (prize_pool_amount >= 0),
    submissions_count INTEGER NOT NULL CHECK (submissions_count >= 0)
);

ALTER TABLE problems
    ALTER COLUMN prize_amount TYPE BIGINT USING prize_amount::BIGINT;

ALTER TABLE problems
    ALTER COLUMN entry_fee_amount TYPE BIGINT USING entry_fee_amount::BIGINT;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS constraints_text TEXT;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS examples_json TEXT;

ALTER TABLE problems
    ADD COLUMN IF NOT EXISTS reference_solution_hash VARCHAR(66);

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

UPDATE problems
SET constraints_text = COALESCE(constraints_text, '')
WHERE constraints_text IS NULL;

UPDATE problems
SET examples_json = COALESCE(NULLIF(examples_json, ''), '[]')
WHERE examples_json IS NULL OR examples_json = '';

UPDATE problems
SET reference_solution_hash = COALESCE(NULLIF(reference_solution_hash, ''), '0x')
WHERE reference_solution_hash IS NULL OR reference_solution_hash = '';

ALTER TABLE problems
    ALTER COLUMN constraints_text SET DEFAULT '';

ALTER TABLE problems
    ALTER COLUMN examples_json SET DEFAULT '[]';

ALTER TABLE problems
    ALTER COLUMN reference_solution_hash SET DEFAULT '0x';

ALTER TABLE problems
    ALTER COLUMN constraints_text SET NOT NULL;

ALTER TABLE problems
    ALTER COLUMN examples_json SET NOT NULL;

ALTER TABLE problems
    ALTER COLUMN reference_solution_hash SET NOT NULL;

ALTER TABLE problem_winners
    ALTER COLUMN payout_amount TYPE BIGINT USING payout_amount::BIGINT;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS source_code TEXT;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS language VARCHAR(32);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS code_hash VARCHAR(66);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS tests_hash VARCHAR(66);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS result_hash VARCHAR(66);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS consensus_image_hash VARCHAR(128);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS consensus_nodes INTEGER;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS commitment_hash VARCHAR(66);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS runtime_ms INTEGER;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS anchor_status VARCHAR(16);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS anchor_batch_id BIGINT;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS anchor_merkle_root VARCHAR(66);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS anchor_merkle_proof_json TEXT;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS anchor_tx_hash VARCHAR(128);

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS anchor_error TEXT;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS anchored_at TIMESTAMPTZ;

UPDATE problem_submissions
SET source_code = COALESCE(source_code, '')
WHERE source_code IS NULL;

UPDATE problem_submissions
SET language = COALESCE(NULLIF(language, ''), 'unknown')
WHERE language IS NULL OR language = '';

UPDATE problem_submissions
SET code_hash = COALESCE(NULLIF(code_hash, ''), '0x')
WHERE code_hash IS NULL OR code_hash = '';

UPDATE problem_submissions
SET tests_hash = COALESCE(NULLIF(tests_hash, ''), '0x')
WHERE tests_hash IS NULL OR tests_hash = '';

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
SET anchor_status = COALESCE(NULLIF(anchor_status, ''), 'disabled')
WHERE anchor_status IS NULL OR anchor_status = '';

UPDATE problem_submissions
SET anchor_merkle_proof_json = COALESCE(NULLIF(anchor_merkle_proof_json, ''), '[]')
WHERE anchor_merkle_proof_json IS NULL OR anchor_merkle_proof_json = '';

ALTER TABLE problem_submissions
    ALTER COLUMN source_code SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN language SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN code_hash SET DEFAULT '0x';

ALTER TABLE problem_submissions
    ALTER COLUMN tests_hash SET DEFAULT '0x';

ALTER TABLE problem_submissions
    ALTER COLUMN result_hash SET DEFAULT '0x';

ALTER TABLE problem_submissions
    ALTER COLUMN consensus_nodes SET DEFAULT 0;

ALTER TABLE problem_submissions
    ALTER COLUMN commitment_hash SET DEFAULT '0x';

ALTER TABLE problem_submissions
    ALTER COLUMN runtime_ms SET DEFAULT 0;

ALTER TABLE problem_submissions
    ALTER COLUMN anchor_status SET DEFAULT 'disabled';

ALTER TABLE problem_submissions
    ALTER COLUMN anchor_merkle_proof_json SET DEFAULT '[]';

ALTER TABLE problem_submissions
    ALTER COLUMN code_hash SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN tests_hash SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN result_hash SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN consensus_nodes SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN commitment_hash SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN runtime_ms SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN anchor_status SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN anchor_merkle_proof_json SET NOT NULL;

DO $$
BEGIN
    ALTER TABLE problem_submissions
        ADD CONSTRAINT chk_problem_submissions_anchor_status
        CHECK (anchor_status IN ('pending', 'anchored', 'failed', 'disabled'));
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

ALTER TABLE dashboard_daily_metrics
    DROP COLUMN IF EXISTS created_at;

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

CREATE INDEX IF NOT EXISTS idx_problem_submissions_user_id
    ON problem_submissions(user_id);

CREATE INDEX IF NOT EXISTS idx_problem_submissions_problem_user
    ON problem_submissions(problem_id, user_id);

CREATE INDEX IF NOT EXISTS idx_problem_submissions_anchor_status
    ON problem_submissions(anchor_status, submitted_at);

CREATE INDEX IF NOT EXISTS idx_problem_submission_attestations_submission_id
    ON problem_submission_attestations(submission_id);

CREATE INDEX IF NOT EXISTS idx_problem_submission_attestations_node_id
    ON problem_submission_attestations(node_id);

CREATE INDEX IF NOT EXISTS idx_submission_anchor_batches_status_created_at
    ON submission_anchor_batches(status, created_at);

CREATE INDEX IF NOT EXISTS idx_problems_created_by_user_id
    ON problems(created_by_user_id);

CREATE INDEX IF NOT EXISTS idx_problems_problem_status
    ON problems(problem_status);

CREATE INDEX IF NOT EXISTS idx_problem_winners_winner_user_id
    ON problem_winners(winner_user_id);

CREATE INDEX IF NOT EXISTS idx_dashboard_daily_metrics_metric_date
    ON dashboard_daily_metrics(metric_date DESC);
