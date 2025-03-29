// This component should be added inside ResultViewer.js
const ResultViewerError = ({ error, onRetry, onNewComparison }) => {
    return (
      <div className="result-viewer-error">
        <div className="error-icon">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
        </div>
        <h3>Error Loading Page Comparison</h3>
        <p className="error-message">{error}</p>
        <p className="error-details">
          Failed to load page comparison details. Request failed with status code 404.
        </p>
        <div className="error-actions">
          <button className="reload-button" onClick={onRetry}>
            Retry
          </button>
          <button className="back-button" onClick={onNewComparison}>
            New Comparison
          </button>
        </div>
      </div>
    );
  };