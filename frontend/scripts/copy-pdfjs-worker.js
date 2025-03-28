/**
 * This script copies the PDF.js worker file to the public directory.
 * Add this to your package.json scripts under "postinstall"
 */

const fs = require('fs');
const path = require('path');

const sourceFile = path.join(
  __dirname,
  '..',
  'node_modules',
  'pdfjs-dist',
  'build',
  'pdf.worker.js'
);

const destFile = path.join(
  __dirname,
  '..',
  'public',
  'pdf.worker.js'
);

// Create a readable stream from the source file
const readable = fs.createReadStream(sourceFile);
// Create a writable stream to the destination file
const writable = fs.createWriteStream(destFile);

// Copy the file
readable.pipe(writable);

console.log('PDF.js worker file copied to public directory');