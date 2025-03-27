import React, { useState } from 'react';
import './App.css';
import PDFUploader from './components/PDFUploader';
import PDFComparer from './components/PDFComparer';
import ResultViewer from './components/ResultViewer';

function App() {
  const [baseFile, setBaseFile] = useState(null);
  const [compareFile, setCompareFile] = useState(null);
  const [comparisonId, setComparisonId] = useState(null);
  const [view, setView] = useState('upload');

  const handleBaseFileUploaded = (fileInfo) => {
    setBaseFile(fileInfo);
  };

  const handleCompareFileUploaded = (fileInfo) => {
    setCompareFile(fileInfo);
  };

  const handleComparisonStarted = (id) => {
    setComparisonId(id);
    setView('results');
  };

  const resetComparison = () => {
    setBaseFile(null);
    setCompareFile(null);
    setComparisonId(null);
    setView('upload');
  };

  return (
    <div className="App">
      <header className="App-header">
        <h1>PDF Comparison Tool</h1>
      </header>
      <main className="App-main">
        {view === 'upload' && (
          <div className="upload-container">
            <div className="file-upload-section">
              <h2>Upload Base PDF</h2>
              <PDFUploader onFileUploaded={handleBaseFileUploaded} />
              {baseFile && (
                <div className="file-info">
                  <p>File: {baseFile.fileName}</p>
                  <p>Size: {Math.round(baseFile.size / 1024)} KB</p>
                </div>
              )}
            </div>
            <div className="file-upload-section">
              <h2>Upload Comparison PDF</h2>
              <PDFUploader onFileUploaded={handleCompareFileUploaded} />
              {compareFile && (
                <div className="file-info">
                  <p>File: {compareFile.fileName}</p>
                  <p>Size: {Math.round(compareFile.size / 1024)} KB</p>
                </div>
              )}
            </div>
            {baseFile && compareFile && (
              <div className="comparison-section">
                <PDFComparer 
                  baseFileId={baseFile.fileId} 
                  compareFileId={compareFile.fileId} 
                  onComparisonStarted={handleComparisonStarted} 
                />
              </div>
            )}
          </div>
        )}

        {view === 'results' && comparisonId && (
          <div className="results-container">
            <ResultViewer comparisonId={comparisonId} />
            <button className="new-comparison-btn" onClick={resetComparison}>
              Start New Comparison
            </button>
          </div>
        )}
      </main>
    </div>
  );
}

export default App;