import React, { useEffect, useRef } from 'react';
import './DifferenceTooltip.css';

/**
 * DifferenceTooltip component - Shows details when hovering over differences
 * @param {Object} props Component props
 * @param {Object} props.difference The difference object to display
 * @param {boolean} props.visible Whether the tooltip is visible
 * @param {number} props.x X coordinate for the tooltip
 * @param {number} props.y Y coordinate for the tooltip
 */
const DifferenceTooltip = ({ difference, visible, x, y }) => {
  const tooltipRef = useRef(null);

  // Update tooltip position
  useEffect(() => {
    if (tooltipRef.current && visible) {
      tooltipRef.current.style.left = `${x}px`;
      tooltipRef.current.style.top = `${y}px`;
    }
  }, [x, y, visible]);

  if (!difference || !visible) {
    return null;
  }

  // Format the difference text based on type
  const getFormattedContent = () => {
    const { type, changeType } = difference;

    // Text difference
    if (type === 'text') {
      return (
        <div className="tooltip-content">
          <div className="tooltip-header">
            <span className="diff-type">Text {changeType || 'Difference'}</span>
          </div>
          
          {difference.baseText && (
            <div className="base-text">
              <span className="label">Base:</span> {difference.baseText}
            </div>
          )}
          
          {difference.compareText && (
            <div className="compare-text">
              <span className="label">Compare:</span> {difference.compareText}
            </div>
          )}
        </div>
      );
    }

    // Image difference
    if (type === 'image') {
      return (
        <div className="tooltip-content">
          <div className="tooltip-header">
            <span className="diff-type">Image {changeType || 'Difference'}</span>
          </div>
          <div className="image-info">
            {difference.imageName && <div>Name: {difference.imageName}</div>}
            {difference.bounds && (
              <div className="dimension-info">
                Size: {difference.bounds.width}x{difference.bounds.height}
              </div>
            )}
          </div>
        </div>
      );
    }

    // Font difference
    if (type === 'font') {
      return (
        <div className="tooltip-content">
          <div className="tooltip-header">
            <span className="diff-type">Font {changeType || 'Difference'}</span>
          </div>
          {difference.fontName && <div>Font: {difference.fontName}</div>}
          {difference.text && <div>Text: "{difference.text}"</div>}
        </div>
      );
    }

    // Style difference
    if (type === 'style') {
      return (
        <div className="tooltip-content">
          <div className="tooltip-header">
            <span className="diff-type">Style {changeType || 'Difference'}</span>
          </div>
          {difference.styleName && <div>Style: {difference.styleName}</div>}
          {difference.text && <div>Text: "{difference.text}"</div>}
        </div>
      );
    }   

    // Default case
    return (
      <div className="tooltip-content">
        <div className="tooltip-header">
          <span className="diff-type">{type || 'Unknown'} Difference</span>
        </div>
        <div>{difference.description || 'No description available'}</div>
      </div>
    );
  };

  return (
    <div 
      ref={tooltipRef}
      className={`difference-tooltip ${visible ? 'visible' : 'hidden'}`}
      style={{ left: x, top: y }}
    >
      {getFormattedContent()}
    </div>
  );
};

export default DifferenceTooltip;