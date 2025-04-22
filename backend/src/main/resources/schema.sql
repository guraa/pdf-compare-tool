-- Drop tables if they exist (comment this out after first run)
-- DROP TABLE IF EXISTS comparisons;
-- DROP TABLE IF EXISTS pdf_documents;

-- Create the PDF Documents table
CREATE TABLE IF NOT EXISTS pdf_documents (
                                             file_id VARCHAR(255) PRIMARY KEY,
                                             file_name VARCHAR(255),
                                             file_path VARCHAR(512),
                                             page_count INT,
                                             content_hash VARCHAR(255),
                                             upload_date TIMESTAMP,
                                             title VARCHAR(255),
                                             author VARCHAR(255),
                                             subject VARCHAR(255),
                                             keywords VARCHAR(512),
                                             creator VARCHAR(255),
                                             producer VARCHAR(255),
                                             creation_date VARCHAR(255),
                                             modification_date VARCHAR(255),
                                             encrypted BOOLEAN
);

-- Create the Comparisons table
CREATE TABLE IF NOT EXISTS comparisons (
                                           id VARCHAR(255) PRIMARY KEY,
                                           base_document_id VARCHAR(255) NOT NULL,
                                           compare_document_id VARCHAR(255) NOT NULL,
                                           status VARCHAR(50) NOT NULL,
                                           error_message VARCHAR(2000),
                                           progress INT DEFAULT 0,
                                           total_operations INT DEFAULT 0,
                                           completed_operations INT DEFAULT 0,
                                           current_phase VARCHAR(100) DEFAULT 'Initializing',
                                           created_at TIMESTAMP NOT NULL,
                                           updated_at TIMESTAMP NOT NULL,
                                           FOREIGN KEY (base_document_id) REFERENCES pdf_documents(file_id),
                                           FOREIGN KEY (compare_document_id) REFERENCES pdf_documents(file_id)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_pdf_docs_content_hash ON pdf_documents(content_hash);
CREATE INDEX IF NOT EXISTS idx_comparisons_base_doc ON comparisons(base_document_id);
CREATE INDEX IF NOT EXISTS idx_comparisons_compare_doc ON comparisons(compare_document_id);
CREATE INDEX IF NOT EXISTS idx_comparisons_status ON comparisons(status);