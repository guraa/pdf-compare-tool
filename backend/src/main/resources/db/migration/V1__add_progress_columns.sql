-- Add progress tracking columns to the comparisons table
ALTER TABLE comparisons ADD COLUMN IF NOT EXISTS progress INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE comparisons ADD COLUMN IF NOT EXISTS total_operations INTEGER DEFAULT 0;
ALTER TABLE comparisons ADD COLUMN IF NOT EXISTS completed_operations INTEGER DEFAULT 0;
ALTER TABLE comparisons ADD COLUMN IF NOT EXISTS current_phase VARCHAR(100) DEFAULT 'Initializing';
