CREATE TABLE users (
        id BIGSERIAL PRIMARY KEY,
        email VARCHAR(100) NOT NULL UNIQUE,
        password_hash VARCHAR(100) NOT NULL,
        first_name VARCHAR(50)  NOT NULL,
        last_name VARCHAR(50)  NOT NULL,
        role VARCHAR(20)  NOT NULL,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
        CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'))
);