import React from 'react';
import './DifferenceLegend.css';

/**
 * Component for displaying a legend explaining the different types of changes
 * Similar to i-net PDFC's legend for understanding color coding
 */
const DifferenceLegend = ({ differences }) => {
  // Count differences by type and change type
  const getCounts = () => {
    if (!differences) return {};
    
    const counts = {
      added: 0,
      deleted: 0,
      modified: 0,
      text: 0,
      image: 0,
      font: 0,
      style: 0
    };
    
    // Process both base and compare differences
    const allDiffs = [
      ...(differences.baseDifferences || []),
      ...(differences.compareDifferences || [])
    ];
    
    // Use a Set to store unique IDs to avoid counting duplicates
    const uniqueIds = new Set();
    
    allDiffs.forEach(diff => {
      // Skip if we've already counted this difference
      if (uniqueIds.has(diff.id)) return;
      uniqueIds.add(diff.id);
      
      // Count by change type
      if (diff.changeType) {
        counts[diff.changeType] = (counts[diff.changeType] || 0) + 1;
      }
      
      // Count by difference type
      if (diff.type) {
        counts[diff.type] = (counts[diff.type] || 0) + 1;
      }
    });
    
    return counts;
  };
  
  const counts = getCounts();

  return (
    <div className="difference-legend">
      <div className="legend-section change-types">
        <h4>Change Types</h4>
        <div className="legend-items">
          <div className="legend-item">
            <div className="legend-color added"></div>
            <div className="legend-label">
              Added
              {counts.added > 0 && <span className="count">({counts.added})</span>}
            </div>
          </div>
          <div className="legend-item">
            <div className="legend-color deleted"></div>
            <div className="legend-label">
              Deleted
              {counts.deleted > 0 && <span className="count">({counts.deleted})</span>}
            </div>
          </div>
          <div className="legend-item">
            <div className="legend-color modified"></div>
            <div className="legend-label">
              Modified
              {counts.modified > 0 && <span className="count">({counts.modified})</span>}
            </div>
          </div>
        </div>
      </div>
      
      <div className="legend-section difference-types">
        <h4>Difference Types</h4>
        <div className="legend-items">
          <div className="legend-item">
            <div className="legend-color text"></div>
            <div className="legend-label">
              Text
              {counts.text > 0 && <span className="count">({counts.text})</span>}
            </div>
          </div>
          <div className="legend-item">
            <div className="legend-color image"></div>
            <div className="legend-label">
              Images
              {counts.image > 0 && <span className="count">({counts.image})</span>}
            </div>
          </div>
          <div className="legend-item">
            <div className="legend-color font"></div>
            <div className="legend-label">
              Fonts
              {counts.font > 0 && <span className="count">({counts.font})</span>}
            </div>
          </div>
          <div className="legend-item">
            <div className="legend-color style"></div>
            <div className="legend-label">
              Styles
              {counts.style > 0 && <span className="count">({counts.style})</span>}
            </div>
          </div>
        </div>
      </div>
      
      <div className="legend-help">
        <div className="help-icon">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z" />
          </svg>
        </div>
        <div className="help-text">
          Click on any highlighted difference to select it and view details
        </div>
      </div>
    </div>
  );
};

export default DifferenceLegend;