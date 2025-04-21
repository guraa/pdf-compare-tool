import React, { useEffect, useState } from 'react';
import './PDFBattleLoadingScreen.css';

// More engaging messages
const messages = [
  "Loading Document A...",
  "Loading Document B...",
  "Analyzing Layout Structure...",
  "Comparing Text Content...",
  "Scanning for Image Differences...",
  "Checking Font Styles...",
  "Finalizing Comparison...",
  "Almost there!",
];

// SVG Icon for a PDF document
const PdfIcon = () => (
  <svg className="pdf-icon" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
    <path d="M14 2H6C4.9 2 4 2.9 4 4V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V8L14 2ZM18 20H6V4H13V9H18V20Z" />
    <path d="M11 18H13V15H16V13H13V10H11V13H8V15H11V18Z" />
  </svg>
);

// SVG Icon for the "versus" graphic
const VersusIcon = () => (
  <svg className="versus-icon" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
    <line x1="20" y1="20" x2="80" y2="80" stroke="currentColor" strokeWidth="8" />
    <line x1="80" y1="20" x2="20" y2="80" stroke="currentColor" strokeWidth="8" />
  </svg>
);

const PDFBattleLoadingScreen = ({ progress = 0, currentPhase = "Initializing" }) => {
  const [messageIndex, setMessageIndex] = useState(0);

  // Cycle through messages
  useEffect(() => {
    const interval = setInterval(() => {
      setMessageIndex((prev) => (prev + 1) % messages.length);
    }, 2500); // Slightly longer interval
    return () => clearInterval(interval);
  }, []);

  // Determine the current message based on phase or cycle
  const displayMessage = currentPhase !== "Initializing" ? currentPhase : messages[messageIndex];

  return (
    <div className="pdf-battle-loading-screen">
      <div className="arena">
        <div className="pdf-container pdf-left">
          <PdfIcon />
          <span>Document A</span>
        </div>
        <div className="versus-container">
          <VersusIcon />
        </div>
        <div className="pdf-container pdf-right">
          <PdfIcon />
          <span>Document B</span>
        </div>
      </div>
      <div className="status-container">
        <div className="loading-message">{displayMessage}</div>
        <div className="progress-bar-container">
          <div
            className="progress-bar"
            style={{ width: `${Math.min(progress, 100)}%` }} // Use actual progress
          />
        </div>
        <div className="progress-text">{Math.min(progress, 100)}% Complete</div>
      </div>
    </div>
  );
};

export default PDFBattleLoadingScreen;
