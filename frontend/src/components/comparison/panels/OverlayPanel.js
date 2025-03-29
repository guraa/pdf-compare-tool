import React, { useRef, useEffect } from 'react';
import PDFRenderer from '../PDFRenderer';

const OverlayPanel = ({
  result,
  pageDetails,
  loading,
  state,
  handleDifferenceSelect,
  showDifferencePanel,
  overlayOpacity
}) => {
  const overlayContainerRef = useRef(null);

  return (
    <div className={`overlay-container ${showDifferencePanel ? 'with-panel' : 'full-width'}`}>
      <div className="document-viewer overlay-viewer">
        <div className="document-header">
          <h3>Overlay View</h3>
          <div className="overlay-legend">
            <span className="base-legend">Base Document</span>
            <span className="compare-legend">Comparison Document</span>
          </div>
        </div>
        
        <div 
          className="overlay-content" 
          ref={overlayContainerRef}
        >
          <div className="overlay-layers" style={{ position: 'relative' }}>
            {/* Base document layer */}
            <div 
              className="base-layer"
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                zIndex: 1
              }}
            >
              {result.basePageCount >= state.selectedPage && (
                <PDFRenderer 
                  fileId={state.baseFile?.fileId}
                  page={state.selectedPage}
                  zoom={state.viewSettings.zoom}
                  highlightMode="none"
                  differences={[]}
                  selectedDifference={null}
                  onDifferenceSelect={()=>{}}
                  loading={loading}
                  interactive={false}
                  opacity={1.0}
                />
              )}
            </div>
            
            {/* Compare document layer with blending */}
            <div 
              className="compare-layer" 
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                zIndex: 2,
                mixBlendMode: 'difference',
                opacity: overlayOpacity
              }}
            >
              {result.comparePageCount >= state.selectedPage && (
                <PDFRenderer 
                  fileId={state.compareFile?.fileId}
                  page={state.selectedPage}
                  zoom={state.viewSettings.zoom}
                  highlightMode="none"
                  differences={[]}
                  selectedDifference={null}
                  onDifferenceSelect={()=>{}}
                  loading={loading}
                  interactive={false}
                  opacity={1.0}
                />
              )}
            </div>
            
            {/* Highlight layer on top */}
            <div 
              className="highlight-layer" 
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                zIndex: 3,
                pointerEvents: 'auto'
              }}
            >
              {/* Add transparent canvas with highlights */}
              <div 
                style={{
                  position: 'relative',
                  width: '100%',
                  height: '100%'
                }}
              >
                {pageDetails && pageDetails.baseDifferences && (
                  pageDetails.baseDifferences.map(diff => (
                    <div 
                      key={diff.id}
                      className="difference-highlight"
                      style={{
                        position: 'absolute',
                        top: diff.position?.y || 0,
                        left: diff.position?.x || 0,
                        width: diff.bounds?.width || 100,
                        height: diff.bounds?.height || 30,
                        backgroundColor: 'rgba(255, 0, 0, 0.3)',
                        border: '2px solid rgba(255, 0, 0, 0.5)',
                        borderRadius: '4px',
                        zIndex: 4,
                        cursor: 'pointer'
                      }}
                      onClick={() => handleDifferenceSelect(diff)}
                    />
                  ))
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default OverlayPanel;