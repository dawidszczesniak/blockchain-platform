-- 3NF schema:
-- 1) users: identities and account activity timestamps.
-- 2) problems: core problem attributes and owner relation.
-- 3) problem_participants: many-to-many registrations.
-- 4) problem_submissions: attempt history (many submissions per user and problem).
-- 5) problem_winners: history of user wins per problem.

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
    prize_amount INTEGER NOT NULL CHECK (prize_amount >= 0),
    entry_fee_amount INTEGER NOT NULL CHECK (entry_fee_amount >= 0),
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

CREATE TABLE IF NOT EXISTS problem_submissions (
    submission_id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('accepted', 'rejected', 'error')),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (problem_id, user_id)
        REFERENCES problem_participants(problem_id, user_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS problem_winners (
    problem_id BIGINT NOT NULL,
    winner_user_id BIGINT NOT NULL,
    payout_amount INTEGER NOT NULL CHECK (payout_amount >= 0),
    won_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (problem_id, winner_user_id),
    FOREIGN KEY (problem_id, winner_user_id)
        REFERENCES problem_participants(problem_id, user_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_problem_participants_problem_id
    ON problem_participants(problem_id);

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
