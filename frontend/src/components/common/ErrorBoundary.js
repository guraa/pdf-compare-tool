import React, { Component } from 'react';
import './ErrorBoundary.css';

class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error) {
    // Update state so the next render will show the fallback UI
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    // Log the error to an error reporting service
    console.error('Error caught by ErrorBoundary:', error, errorInfo);
    this.setState({ error, errorInfo });
  }

  render() {
    if (this.state.hasError) {
      // Custom fallback UI
      return (
        <div className="error-boundary">
          <div className="error-container">
            <div className="error-icon">
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
              </svg>
            </div>
            <h2>Something went wrong</h2>
            <p>We're sorry, but we encountered an error while rendering this component.</p>
            <div className="error-actions">
              <button onClick={() => window.location.reload()}>
                Reload Page
              </button>
            </div>
            {process.env.NODE_ENV === 'development' && (
              <div className="error-details">
                <h3>Error Details:</h3>
                <p className="error-message">{this.state.error && this.state.error.toString()}</p>
                <div className="error-stack">
                  {this.state.errorInfo && this.state.errorInfo.componentStack}
                </div>
              </div>
            )}
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;