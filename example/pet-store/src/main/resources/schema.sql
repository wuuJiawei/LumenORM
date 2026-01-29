-- Pet Store Database Schema for LumenORM Demo
-- Uses H2 in-memory database

-- Drop tables if exist (for clean restarts)
DROP TABLE IF EXISTS pets;
DROP TABLE IF EXISTS owners;
DROP TABLE IF EXISTS categories;

-- Create owners table
CREATE TABLE owners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    address VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create categories table
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500)
);

-- Create pets table
CREATE TABLE pets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    species VARCHAR(100) NOT NULL,
    breed VARCHAR(255),
    age INT DEFAULT 0,
    price DECIMAL(10, 2) DEFAULT 0.00,
    owner_id BIGINT REFERENCES owners(id),
    birth_date DATE,
    available BOOLEAN DEFAULT TRUE
);

-- Create indexes for common queries
CREATE INDEX idx_pets_species ON pets(species);
CREATE INDEX idx_pets_available ON pets(available);
CREATE INDEX idx_pets_owner ON pets(owner_id);
CREATE INDEX idx_owners_email ON owners(email);

-- Insert sample data
INSERT INTO categories (name, description) VALUES
    ('Dogs', 'Canine companions'),
    ('Cats', 'Feline friends'),
    ('Birds', 'Feathered pets'),
    ('Fish', 'Aquatic companions'),
    ('Small Animals', 'Hamsters, rabbits, etc.');

INSERT INTO owners (name, email, phone, address) VALUES
    ('John Smith', 'john@example.com', '555-0101', '123 Main St, Springfield'),
    ('Jane Doe', 'jane@example.com', '555-0102', '456 Oak Ave, Springfield'),
    ('Bob Wilson', 'bob@example.com', '555-0103', '789 Pine Rd, Springfield');

INSERT INTO pets (name, species, breed, age, price, owner_id, birth_date, available) VALUES
    ('Fluffy', 'Cat', 'Persian', 2, 599.99, NULL, '2022-01-15', TRUE),
    ('Buddy', 'Dog', 'Golden Retriever', 3, 899.99, 1, '2021-06-20', TRUE),
    ('Whiskers', 'Cat', 'Siamese', 1, 449.99, NULL, '2023-03-10', TRUE),
    ('Max', 'Dog', 'German Shepherd', 4, 799.99, 2, '2020-11-05', TRUE),
    ('Tweety', 'Bird', 'Canary', 2, 99.99, NULL, '2022-08-15', TRUE),
    ('Goldie', 'Fish', 'Goldfish', 1, 29.99, NULL, '2023-01-20', TRUE),
    ('Hoppy', 'Rabbit', 'Dutch', 1, 79.99, NULL, '2023-02-28', TRUE),
    ('Speedy', 'Hamster', 'Syrian', 1, 39.99, NULL, '2023-04-10', TRUE);
