-- Fix nullable operations columns by setting null values to 0 and adding NOT NULL constraint
UPDATE comparisons SET total_operations = 0 WHERE total_operations IS NULL;
UPDATE comparisons SET completed_operations = 0 WHERE completed_operations IS NULL;

-- Add NOT NULL constraints
ALTER TABLE comparisons ALTER COLUMN total_operations SET NOT NULL;
ALTER TABLE comparisons ALTER COLUMN completed_operations SET NOT NULL;
