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

UPDATE problems
SET constraints_text = COALESCE(constraints_text, '')
WHERE constraints_text IS NULL;

UPDATE problems
SET examples_json = COALESCE(NULLIF(examples_json, ''), '[]')
WHERE examples_json IS NULL OR examples_json = '';

ALTER TABLE problems
    ALTER COLUMN constraints_text SET DEFAULT '';

ALTER TABLE problems
    ALTER COLUMN examples_json SET DEFAULT '[]';

ALTER TABLE problems
    ALTER COLUMN constraints_text SET NOT NULL;

ALTER TABLE problems
    ALTER COLUMN examples_json SET NOT NULL;

ALTER TABLE problem_winners
    ALTER COLUMN payout_amount TYPE BIGINT USING payout_amount::BIGINT;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS source_code TEXT;

ALTER TABLE problem_submissions
    ADD COLUMN IF NOT EXISTS language VARCHAR(32);

UPDATE problem_submissions
SET source_code = COALESCE(source_code, '')
WHERE source_code IS NULL;

UPDATE problem_submissions
SET language = COALESCE(NULLIF(language, ''), 'unknown')
WHERE language IS NULL OR language = '';

ALTER TABLE problem_submissions
    ALTER COLUMN source_code SET NOT NULL;

ALTER TABLE problem_submissions
    ALTER COLUMN language SET NOT NULL;

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
    ALTER COLUMN input_data SET NOT NULL;

ALTER TABLE problem_tests
    ALTER COLUMN expected_output SET NOT NULL;

ALTER TABLE problem_tests
    ALTER COLUMN is_hidden SET NOT NULL;

ALTER TABLE problem_tests
    ALTER COLUMN timeout_ms SET NOT NULL;

ALTER TABLE problem_tests
    ALTER COLUMN memory_limit_mb SET NOT NULL;

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

CREATE INDEX IF NOT EXISTS idx_problems_created_by_user_id
    ON problems(created_by_user_id);

CREATE INDEX IF NOT EXISTS idx_problems_problem_status
    ON problems(problem_status);

CREATE INDEX IF NOT EXISTS idx_problem_winners_winner_user_id
    ON problem_winners(winner_user_id);

CREATE INDEX IF NOT EXISTS idx_dashboard_daily_metrics_metric_date
    ON dashboard_daily_metrics(metric_date DESC);
