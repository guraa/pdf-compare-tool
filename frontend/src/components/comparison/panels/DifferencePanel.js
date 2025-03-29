import React from 'react';
import Spinner from '../../common/Spinner';
import PDFRenderer from '../PDFRenderer';

const DifferencePanel = ({
  result,
  pageDetails,
  baseContainerRef,
  loading,
  state,
  handleDifferenceSelect,
  showDifferencePanel
}) => {
  if (!pageDetails) return <div className="loading-container"><Spinner size="medium" /></div>;
  
  // Combine all differences
  const allDifferences = [
    ...(pageDetails.baseDifferences || []),
    ...(pageDetails.compareDifferences || []).filter(diff => 
      !pageDetails.baseDifferences.some(baseDiff => baseDiff.id === diff.id)
    )
  ];
  
  return (
    <div className={`difference-only-container ${showDifferencePanel ? 'with-panel' : 'full-width'}`}>
      <div className="document-viewer difference-only-viewer">
        <div className="document-header">
          <h3>Difference View</h3>
          <div className="difference-legend">
            <span className="added-legend">Added</span>
            <span className="deleted-legend">Deleted</span>
            <span className="modified-legend">Modified</span>
          </div>
        </div>
        
        <div className="difference-content" ref={baseContainerRef}>
          <div className="difference-renderer" style={{ position: 'relative' }}>
            {result.basePageCount >= state.selectedPage && (
              <div className="difference-view">
                {/* Base document for reference (with lower opacity) */}
                <div className="reference-layer" style={{ position: 'relative' }}>
                  <PDFRenderer 
                    fileId={state.baseFile?.fileId}
                    page={state.selectedPage}
                    zoom={state.viewSettings.zoom}
                    highlightMode="none"
                    differences={[]}
                    onDifferenceSelect={() => {}}
                    loading={loading}
                    interactive={false}
                    opacity={0.3}
                  />
                </div>
                
                {/* Layer for added content (only in comparison document) */}
                <div className="added-content" style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', zIndex: 10 }}>
                  {allDifferences
                    .filter(diff => diff.changeType === 'added')
                    .map(diff => (
                      <div 
                        key={diff.id}
                        className="diff-item added"
                        style={{
                          position: 'absolute',
                          top: diff.position?.y || 0,
                          left: diff.position?.x || 0,
                          width: diff.bounds?.width || 100,
                          height: diff.bounds?.height || 30,
                          backgroundColor: 'rgba(76, 175, 80, 0.2)',
                          border: '2px solid #4CAF50',
                          borderRadius: '4px',
                          zIndex: 11,
                          cursor: 'pointer',
                          overflow: 'hidden'
                        }}
                        onClick={() => handleDifferenceSelect(diff)}
                      >
                        <div className="diff-content" style={{padding: '4px', fontSize: '0.85rem'}}>
                          {diff.text || diff.description || 'Added content'}
                        </div>
                      </div>
                    ))}
                </div>
                
                {/* Layer for deleted content (only in base document) */}
                <div className="deleted-content" style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', zIndex: 10 }}>
                  {allDifferences
                    .filter(diff => diff.changeType === 'deleted')
                    .map(diff => (
                      <div 
                        key={diff.id}
                        className="diff-item deleted"
                        style={{
                          position: 'absolute',
                          top: diff.position?.y || 0,
                          left: diff.position?.x || 0,
                          width: diff.bounds?.width || 100,
                          height: diff.bounds?.height || 30,
                          backgroundColor: 'rgba(244, 67, 54, 0.2)',
                          border: '2px solid #F44336',
                          borderRadius: '4px',
                          zIndex: 12,
                          cursor: 'pointer',
                          overflow: 'hidden'
                        }}
                        onClick={() => handleDifferenceSelect(diff)}
                      >
                        <div className="diff-content" style={{padding: '4px', fontSize: '0.85rem'}}>
                          {diff.text || diff.description || 'Deleted content'}
                        </div>
                      </div>
                    ))}
                </div>
                
                {/* Layer for modified content */}
                <div className="modified-content" style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', zIndex: 10 }}>
                  {allDifferences
                    .filter(diff => diff.changeType === 'modified')
                    .map(diff => (
                      <div 
                        key={diff.id}
                        className="diff-item modified"
                        style={{
                          position: 'absolute',
                          top: diff.position?.y || 0,
                          left: diff.position?.x || 0,
                          width: diff.bounds?.width || 100,
                          height: diff.bounds?.height || 30,
                          backgroundColor: 'rgba(255, 152, 0, 0.2)',
                          border: '2px solid #FF9800',
                          borderRadius: '4px',
                          zIndex: 13,
                          cursor: 'pointer',
                          overflow: 'hidden'
                        }}
                        onClick={() => handleDifferenceSelect(diff)}
                      >
                        <div className="diff-content" style={{padding: '4px', fontSize: '0.85rem'}}>
                          {diff.baseText && diff.compareText ? (
                            <>
                              <div style={{textDecoration: 'line-through', color: '#F44336'}}>
                                {diff.baseText}
                              </div>
                              <div style={{color: '#4CAF50'}}>
                                {diff.compareText}
                              </div>
                            </>
                          ) : (
                            diff.description || 'Modified content'
                          )}
                        </div>
                      </div>
                    ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default DifferencePanel;