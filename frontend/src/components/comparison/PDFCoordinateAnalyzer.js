import React, { useState, useEffect } from 'react';

/**
 * PDFCoordinateAnalyzer - A diagnostic component for analyzing and debugging
 * PDF coordinate transformation issues.
 * 
 * This component should be added near the top of your SideBySideView to help
 * diagnose the coordinate transformation issues.
 */
const PDFCoordinateAnalyzer = ({ 
  comparisonId, 
  differences = [], 
  pageHeight = 792, // Standard letter height
  onApplyCorrection 
}) => {
  const [algorithm, setAlgorithm] = useState('quadratic');
  const [maxAdjustment, setMaxAdjustment] = useState(15);
  const [results, setResults] = useState(null);
  const [expanded, setExpanded] = useState(false);

  // Analyze differences to find patterns in alignment issues
  useEffect(() => {
    if (!differences || differences.length === 0) return;
    
    // Group differences by vertical position (in 100pt sections)
    const positionGroups = {};
    let totalBaseY = 0;
    let totalCompareY = 0;
    let count = 0;
    
    differences.forEach(diff => {
      // Get Y coordinates from whatever format they're in
      let baseY, compareY;
      
      if (diff.baseY !== undefined) {
        baseY = diff.baseY;
      } else if (diff.position?.y !== undefined) {
        baseY = diff.position.y;
      } else if (diff.y !== undefined) {
        baseY = diff.y;
      }
      
      if (diff.compareY !== undefined) {
        compareY = diff.compareY;
      } else if (diff.position?.y !== undefined) {
        compareY = diff.position.y;
      } else if (diff.y !== undefined) {
        compareY = diff.y;
      }
      
      // Skip if we don't have both coordinates
      if (baseY === undefined || compareY === undefined) return;
      
      // Group by 100pt vertical sections
      const section = Math.floor(baseY / 100) * 100;
      
      if (!positionGroups[section]) {
        positionGroups[section] = {
          count: 0,
          baseY: 0,
          compareY: 0,
          differences: []
        };
      }
      
      positionGroups[section].count++;
      positionGroups[section].baseY += baseY;
      positionGroups[section].compareY += compareY;
      positionGroups[section].differences.push({
        id: diff.id,
        baseY,
        compareY,
        text: diff.text || diff.baseText || diff.compareText || ''
      });
      
      totalBaseY += baseY;
      totalCompareY += compareY;
      count++;
    });
    
    // Calculate average Y position and offset for each group
    const groupResults = Object.keys(positionGroups).map(section => {
      const group = positionGroups[section];
      const avgBaseY = group.baseY / group.count;
      const avgCompareY = group.compareY / group.count;
      const avgOffset = avgCompareY - avgBaseY;
      
      return {
        section: parseInt(section),
        count: group.count,
        avgBaseY,
        avgCompareY,
        avgOffset,
        examples: group.differences.slice(0, 3) // First 3 examples
      };
    }).sort((a, b) => a.section - b.section);
    
    // Calculate overall average
    const overallOffset = count > 0 ? (totalCompareY - totalBaseY) / count : 0;
    
    setResults({
      totalDifferences: count,
      overallOffset,
      groupResults
    });
    
  }, [differences]);
  
  // Apply the selected correction algorithm to a y-coordinate
  const applyCorrection = (y) => {
    if (!y) return y;
    
    const normalizedY = y / pageHeight;
    let adjustment = 0;
    
    switch (algorithm) {
      case 'linear':
        // Linear adjustment (top to bottom)
        adjustment = maxAdjustment * (1 - normalizedY);
        break;
      
      case 'quadratic':
        // Quadratic adjustment (parabolic - max in middle)
        adjustment = 4 * maxAdjustment * normalizedY * (1 - normalizedY);
        break;
      
      case 'sigmoid':
        // Sigmoid adjustment (S-curve)
        const sigmoid = 1 / (1 + Math.exp(-10 * (normalizedY - 0.5)));
        adjustment = maxAdjustment * (1 - sigmoid);
        break;
      
      case 'inversed':
        // Inverse quadratic (min in middle, max at top/bottom)
        adjustment = maxAdjustment * (1 - 4 * normalizedY * (1 - normalizedY));
        break;
    }
    
    return y - adjustment;
  };
  
  // Apply and visualize correction
  const handleApplyCorrection = () => {
    if (onApplyCorrection) {
      onApplyCorrection(algorithm, maxAdjustment);
    }
  };
  
  // Helper to format decimal numbers
  const formatNumber = (num) => {
    return parseFloat(num).toFixed(1);
  };
  
  return (
    <div style={{
      position: 'fixed',
      top: '10px',
      right: '10px',
      width: expanded ? '400px' : '32px',
      zIndex: 9999,
      backgroundColor: 'rgba(33, 33, 33, 0.95)',
      borderRadius: '4px',
      boxShadow: '0 2px 10px rgba(0,0,0,0.3)',
      color: 'white',
      transition: 'width 0.3s ease',
      overflow: 'hidden'
    }}>
      {/* Toggle button */}
      <div 
        style={{
          position: 'absolute',
          top: '0',
          left: '0',
          width: '32px',
          height: '32px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: '#2196F3',
          cursor: 'pointer',
          borderRadius: '4px 0 0 4px'
        }}
        onClick={() => setExpanded(!expanded)}
      >
        {expanded ? '«' : '»'}
      </div>
      
      {/* Main content */}
      <div style={{
        padding: '10px 10px 10px 40px',
        maxHeight: '80vh',
        overflowY: 'auto'
      }}>
        <h3 style={{ margin: '0 0 10px 0', fontSize: '16px' }}>PDF Coordinate Analyzer</h3>
        
        {!expanded ? null : (
          <>
            <div style={{ marginBottom: '15px' }}>
              <div style={{ marginBottom: '8px' }}>
                <label style={{ display: 'block', marginBottom: '5px' }}>Correction Algorithm:</label>
                <select 
                  value={algorithm}
                  onChange={(e) => setAlgorithm(e.target.value)}
                  style={{ 
                    width: '100%', 
                    padding: '5px',
                    backgroundColor: '#444',
                    border: '1px solid #666',
                    color: 'white',
                    borderRadius: '3px'
                  }}
                >
                  <option value="linear">Linear (Top → Bottom)</option>
                  <option value="quadratic">Quadratic (Middle Peak)</option>
                  <option value="sigmoid">Sigmoid (S-curve)</option>
                  <option value="inversed">Inverse (Midpoint Valley)</option>
                </select>
              </div>
              
              <div style={{ marginBottom: '8px' }}>
                <label style={{ display: 'block', marginBottom: '5px' }}>
                  Max Adjustment: {maxAdjustment}px
                </label>
                <input 
                  type="range" 
                  min="0" 
                  max="50" 
                  value={maxAdjustment}
                  onChange={(e) => setMaxAdjustment(parseInt(e.target.value))}
                  style={{ width: '100%' }}
                />
              </div>
              
              <button
                onClick={handleApplyCorrection}
                style={{
                  width: '100%',
                  padding: '8px',
                  backgroundColor: '#4CAF50',
                  color: 'white',
                  border: 'none',
                  borderRadius: '3px',
                  cursor: 'pointer',
                  marginTop: '10px'
                }}
              >
                Apply Correction
              </button>
            </div>
            
            {!results ? (
              <div style={{ color: '#BBB' }}>No difference data to analyze</div>
            ) : (
              <div>
                <div style={{ marginBottom: '10px', borderBottom: '1px solid #555', paddingBottom: '10px' }}>
                  <div>Total differences: {results.totalDifferences}</div>
                  <div>Overall Y offset: {formatNumber(results.overallOffset)}pt</div>
                </div>
                
                <div style={{ marginBottom: '10px' }}>
                  <h4 style={{ margin: '0 0 8px 0', fontSize: '14px' }}>Vertical Section Analysis</h4>
                  
                  <div style={{ fontSize: '12px' }}>
                    {results.groupResults.map(group => (
                      <div key={group.section} style={{ 
                        marginBottom: '15px', 
                        padding: '8px', 
                        backgroundColor: 'rgba(255,255,255,0.1)',
                        borderRadius: '3px'
                      }}>
                        <div style={{ marginBottom: '5px', fontWeight: 'bold' }}>
                          Y: {group.section} to {group.section + 100} ({group.count} differences)
                        </div>
                        <div>Average Y offset: <span style={{ color: '#81D4FA' }}>{formatNumber(group.avgOffset)}pt</span></div>
                        
                        {/* Offset visualization */}
                        <div style={{ 
                          height: '20px', 
                          background: 'linear-gradient(to right, #222, #444)', 
                          position: 'relative',
                          margin: '5px 0',
                          borderRadius: '2px'
                        }}>
                          <div style={{
                            position: 'absolute',
                            top: '0',
                            left: '50%',
                            height: '100%',
                            width: '1px',
                            backgroundColor: '#FFF'
                          }}></div>
                          
                          <div style={{
                            position: 'absolute',
                            top: '0',
                            left: `calc(50% + ${group.avgOffset * 2}px)`,
                            height: '100%',
                            width: '3px',
                            backgroundColor: group.avgOffset > 0 ? '#F44336' : '#4CAF50',
                            transform: 'translateX(-50%)'
                          }}></div>
                        </div>
                        
                        {/* Example items */}
                        <div style={{ fontSize: '11px', color: '#BBB', marginTop: '5px' }}>
                          {group.examples.map((ex, i) => (
                            <div key={i} style={{ marginBottom: '3px' }}>
                              &bull; "{ex.text.substring(0, 20)}..." offset: {formatNumber(ex.compareY - ex.baseY)}pt
                            </div>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
            
            {/* Correction visualization */}
            <div style={{ marginTop: '15px' }}>
              <h4 style={{ margin: '0 0 8px 0', fontSize: '14px' }}>Correction Preview</h4>
              
              <div style={{ 
                height: '200px', 
                backgroundColor: '#212121', 
                position: 'relative',
                border: '1px solid #555',
                marginBottom: '10px',
                borderRadius: '3px'
              }}>
                {/* Original positions (red) */}
                {Array.from({length: 9}).map((_, i) => {
                  const y = i * 100;
                  return (
                    <div key={`orig-${i}`} style={{
                      position: 'absolute',
                      top: `${y * 200/800}px`,
                      left: '5px',
                      width: '170px',
                      height: '2px',
                      backgroundColor: 'rgba(244, 67, 54, 0.7)'
                    }}>
                      <span style={{
                        position: 'absolute',
                        left: '5px',
                        top: '-7px',
                        fontSize: '10px',
                        color: 'rgba(244, 67, 54, 0.9)'
                      }}>
                        Y: {y}
                      </span>
                    </div>
                  );
                })}
                
                {/* Corrected positions (green) */}
                {Array.from({length: 9}).map((_, i) => {
                  const y = i * 100;
                  const correctedY = applyCorrection(y);
                  return (
                    <div key={`corr-${i}`} style={{
                      position: 'absolute',
                      top: `${correctedY * 200/800}px`,
                      left: '180px',
                      width: '170px',
                      height: '2px',
                      backgroundColor: 'rgba(76, 175, 80, 0.7)'
                    }}>
                      <span style={{
                        position: 'absolute',
                        right: '5px',
                        top: '-7px',
                        fontSize: '10px',
                        color: 'rgba(76, 175, 80, 0.9)'
                      }}>
                        Y: {formatNumber(correctedY)}
                      </span>
                    </div>
                  );
                })}
                
                {/* Divider */}
                <div style={{
                  position: 'absolute',
                  top: '0',
                  left: '175px',
                  width: '1px',
                  height: '100%',
                  backgroundColor: '#555'
                }}></div>
                
                {/* Labels */}
                <div style={{
                  position: 'absolute',
                  top: '5px',
                  left: '75px',
                  fontSize: '10px',
                  color: 'rgba(244, 67, 54, 0.9)'
                }}>
                  Original
                </div>
                
                <div style={{
                  position: 'absolute',
                  top: '5px',
                  right: '75px',
                  fontSize: '10px',
                  color: 'rgba(76, 175, 80, 0.9)'
                }}>
                  Corrected
                </div>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default PDFCoordinateAnalyzer;