import React from 'react';

/**
 * DifferenceDebugDisplay - A debug component that displays raw difference data
 * to help diagnose rendering issues.
 */
const DifferenceDebugDisplay = ({ differences }) => {
  if (!differences || differences.length === 0) {
    return (
      <div className="debug-container" style={debugContainerStyle}>
        <h3 style={headerStyle}>Debug: No Differences</h3>
        <p>No differences found to display.</p>
      </div>
    );
  }

  return (
    <div className="debug-container" style={debugContainerStyle}>
      <h3 style={headerStyle}>Debug: {differences.length} Differences</h3>
      
      <div style={differencesListStyle}>
        {differences.slice(0, 10).map((diff, index) => (
          <div key={index} style={differenceItemStyle}>
            <h4 style={diffTitleStyle}>Diff #{index + 1} ({diff.type || 'unknown'})</h4>
            
            <div style={diffDetailsStyle}>
              <div style={diffPropertyStyle}>
                <span style={propLabelStyle}>ID:</span> {diff.id || 'N/A'}
              </div>
              
              {diff.position && (
                <div style={diffPropertyStyle}>
                  <span style={propLabelStyle}>Position:</span> 
                  x: {diff.position.x}, y: {diff.position.y}
                </div>
              )}
              
              {diff.bounds && (
                <div style={diffPropertyStyle}>
                  <span style={propLabelStyle}>Size:</span> 
                  w: {diff.bounds.width}, h: {diff.bounds.height}
                </div>
              )}
              
              {diff.baseX !== undefined && (
                <div style={diffPropertyStyle}>
                  <span style={propLabelStyle}>Base Pos:</span> 
                  x: {diff.baseX}, y: {diff.baseY}, w: {diff.baseWidth}, h: {diff.baseHeight}
                </div>
              )}
              
              {diff.compareX !== undefined && (
                <div style={diffPropertyStyle}>
                  <span style={propLabelStyle}>Compare Pos:</span> 
                  x: {diff.compareX}, y: {diff.compareY}, w: {diff.compareWidth}, h: {diff.compareHeight}
                </div>
              )}
              
              {diff.baseText && (
                <div style={diffPropertyStyle}>
                  <span style={propLabelStyle}>Base Text:</span> 
                  <span style={textContentStyle}>{truncateText(diff.baseText, 30)}</span>
                </div>
              )}
              
              {diff.compareText && (
                <div style={diffPropertyStyle}>
                  <span style={propLabelStyle}>Compare Text:</span> 
                  <span style={textContentStyle}>{truncateText(diff.compareText, 30)}</span>
                </div>
              )}
              
              {diff.text && (
                <div style={diffPropertyStyle}>
                  <span style={propLabelStyle}>Text:</span> 
                  <span style={textContentStyle}>{truncateText(diff.text, 30)}</span>
                </div>
              )}
              
              {diff.changeType && (
                <div style={diffPropertyStyle}>
                  <span style={propLabelStyle}>Change Type:</span> {diff.changeType}
                </div>
              )}
            </div>
          </div>
        ))}
        
        {differences.length > 10 && (
          <div style={moreItemsStyle}>
            + {differences.length - 10} more differences...
          </div>
        )}
      </div>
    </div>
  );
};

// Helper to truncate text for display
const truncateText = (text, maxLength) => {
  if (!text) return '';
  return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
};

// Styles
const debugContainerStyle = {
  position: 'fixed',
  top: '50px',
  right: '10px',
  width: '350px',
  maxHeight: '500px',
  backgroundColor: 'rgba(0, 0, 0, 0.85)',
  color: 'white',
  padding: '10px',
  borderRadius: '5px',
  zIndex: 10000,
  overflowY: 'auto',
  fontFamily: 'monospace',
  fontSize: '12px',
  boxShadow: '0 0 10px rgba(0, 0, 0, 0.5)'
};

const headerStyle = {
  margin: '0 0 10px 0',
  padding: '5px',
  backgroundColor: '#ef5350',
  color: 'white',
  fontSize: '14px',
  textAlign: 'center',
  borderRadius: '3px'
};

const differencesListStyle = {
  display: 'flex',
  flexDirection: 'column',
  gap: '8px'
};

const differenceItemStyle = {
  padding: '8px',
  backgroundColor: 'rgba(50, 50, 50, 0.5)',
  borderRadius: '3px',
  borderLeft: '3px solid #2196F3'
};

const diffTitleStyle = {
  margin: '0 0 5px 0',
  fontSize: '13px',
  color: '#2196F3'
};

const diffDetailsStyle = {
  display: 'flex',
  flexDirection: 'column',
  gap: '4px'
};

const diffPropertyStyle = {
  fontSize: '11px'
};

const propLabelStyle = {
  color: '#8BC34A',
  fontWeight: 'bold',
  marginRight: '4px'
};

const textContentStyle = {
  color: '#FFC107',
  wordBreak: 'break-all'
};

const moreItemsStyle = {
  padding: '5px',
  textAlign: 'center',
  color: '#BDBDBD',
  fontSize: '11px'
};

export default DifferenceDebugDisplay;