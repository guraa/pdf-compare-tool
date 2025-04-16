import React, { useEffect, useRef } from 'react';
import './DifferenceTooltip.css';

/**
 * Enhanced DifferenceTooltip component
 * Shows detailed information when hovering over differences
 */
const DifferenceTooltip = ({ 
  difference, 
  visible, 
  x, 
  y 
}) => {
  const tooltipRef = useRef(null);

  // Position the tooltip relative to cursor
  useEffect(() => {
    if (tooltipRef.current && visible) {
      const tooltip = tooltipRef.current;
      
      // Adjust position to account for tooltip size and viewport edges
      const windowWidth = window.innerWidth;
      const windowHeight = window.innerHeight;
      
      // Get tooltip dimensions
      const tooltipRect = tooltip.getBoundingClientRect();
      const tooltipWidth = tooltipRect.width;
      const tooltipHeight = tooltipRect.height;
      
      // Position tooltip above cursor by default
      let posX = x - tooltipWidth / 2;
      let posY = y - tooltipHeight - 10;
      
      // Adjust if tooltip would go off-screen
      if (posX < 10) posX = 10;
      if (posX + tooltipWidth > windowWidth - 10) posX = windowWidth - tooltipWidth - 10;
      
      // If tooltip would go above viewport, position it below cursor instead
      if (posY < 10) {
        posY = y + 20;
        tooltip.classList.add('below');
      } else {
        tooltip.classList.remove('below');
      }
      
      // Apply position
      tooltip.style.left = `${posX}px`;
      tooltip.style.top = `${posY}px`;
    }
  }, [x, y, visible]);

  // Don't render anything if there's no difference or tooltip is hidden
  if (!difference || !visible) {
    return null;
  }

  // Format the difference content based on type
  const getFormattedContent = () => {
    const { type = 'text', changeType } = difference;

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
          
          {!difference.baseText && !difference.compareText && difference.text && (
            <div className="diff-text">{difference.text}</div>
          )}
          
          {difference.similarityScore !== undefined && (
            <div className="similarity-info">
              Similarity: {Math.round(difference.similarityScore * 100)}%
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
            {difference.description && <div>{difference.description}</div>}
            {difference.bounds && (
              <div className="dimension-info">
                Size: {Math.round(difference.bounds.width)} x {Math.round(difference.bounds.height)} px
              </div>
            )}
            {changeType && (
              <div className="change-info">
                Change: <span className={`change-type ${changeType}`}>{changeType}</span>
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
          {difference.fontName && <div className="font-name">Font: {difference.fontName}</div>}
          {difference.baseFont && difference.compareFont && (
            <div className="font-change">
              <div>From: <span className="base-font">{difference.baseFont}</span></div>
              <div>To: <span className="compare-font">{difference.compareFont}</span></div>
            </div>
          )}
          {difference.text && <div className="text-sample">Text: "{difference.text}"</div>}
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
          {difference.description && <div>{difference.description}</div>}
          {difference.text && <div className="text-sample">Text: "{difference.text}"</div>}
          
          {difference.styleProperties && (
            <div className="style-properties">
              <div className="properties-header">Changed properties:</div>
              <ul className="property-list">
                {Object.entries(difference.styleProperties).map(([key, value]) => (
                  <li key={key}>
                    <span className="property-name">{key}:</span> {value}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      );
    }

    // Default case for other difference types
    return (
      <div className="tooltip-content">
        <div className="tooltip-header">
          <span className="diff-type">{type || 'Unknown'} Difference</span>
        </div>
        <div className="diff-description">{difference.description || 'No description available'}</div>
        
        {difference.position && (
          <div className="position-info">
            Position: x={Math.round(difference.position.x)}, y={Math.round(difference.position.y)}
          </div>
        )}
      </div>
    );
  };

  // Determine tooltip data-type for styling
  const tooltipType = difference.type || 'unknown';
  const tooltipChangeType = difference.changeType || '';

  return (
    <div 
      ref={tooltipRef}
      className={`difference-tooltip ${visible ? 'visible' : 'hidden'}`}
      data-type={tooltipType}
      data-change-type={tooltipChangeType}
    >
      {getFormattedContent()}
    </div>
  );
};

export default DifferenceTooltip;