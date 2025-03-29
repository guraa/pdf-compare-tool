import React, { useRef, useState, useEffect } from 'react';
import PDFRenderer from '../PDFRenderer';

/**
 * Overlay panel component that displays two PDFs overlaid with transparency
 * Fixed to ensure proper rendering and positioning without rendering loops
 */
const OverlayPanel = ({
  result,
  pageDetails,
  loading,
  state,
  handleDifferenceSelect,
  showDifferencePanel,
  overlayOpacity,
  selectedPairIndex = 0,
  isSmartMode = false
}) => {
  const overlayContainerRef = useRef(null);
  const [baseLoaded, setBaseLoaded] = useState(false);
  const [compareLoaded, setCompareLoaded] = useState(false);
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 });

  // Calculate the actual page in base and compare documents
  // Make sure page numbers start at 1, not 0
  let selectedPage = state.selectedPage || 1;
  
  let basePage = selectedPage;
  let comparePage = selectedPage;
  
  if (isSmartMode && result && result.documentPairs && result.documentPairs.length > 0) {
    const documentPair = result.documentPairs[selectedPairIndex];
    if (documentPair) {
      // Adjust for page offsets in document pairs
      basePage = documentPair.baseStartPage + selectedPage - 1;
      comparePage = documentPair.compareStartPage + selectedPage - 1;
    }
  }
  
  // Ensure we never send page 0 to the API
  basePage = Math.max(1, basePage);
  comparePage = Math.max(1, comparePage);
  
  // Check if pages exist
  const basePageExists = isSmartMode && result && result.documentPairs && result.documentPairs.length > 0 ? 
    (basePage >= result.documentPairs[selectedPairIndex].baseStartPage && 
     basePage <= result.documentPairs[selectedPairIndex].baseEndPage) :
    (result && result.basePageCount >= selectedPage);
    
  const comparePageExists = isSmartMode && result && result.documentPairs && result.documentPairs.length > 0 ? 
    (comparePage >= result.documentPairs[selectedPairIndex].compareStartPage && 
     comparePage <= result.documentPairs[selectedPairIndex].compareEndPage) :
    (result && result.comparePageCount >= selectedPage);

  // Handle PDF loading completion
  const handleBaseImageLoaded = (width, height) => {
    console.log('Base image loaded:', width, height);
    setDimensions({ width, height });
    setBaseLoaded(true);
  };

  const handleCompareImageLoaded = (width, height) => {
    console.log('Compare image loaded:', width, height);
    setCompareLoaded(true);
  };

  // Reset loading state when page changes
  useEffect(() => {
    setBaseLoaded(false);
    setCompareLoaded(false);
  }, [basePage, comparePage, state.selectedPage]);

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
          style={{ position: 'relative', minHeight: '600px' }}
        >
          <div className="overlay-layers" style={{ position: 'relative' }}>
            {/* Base document layer */}
            <div className="base-layer" style={{ 
              position: 'absolute', 
              top: 0, 
              left: 0, 
              width: '100%', 
              height: '100%',
              zIndex: 10
            }}>
              {basePageExists && (
                <PDFRenderer 
                  fileId={state.baseFile?.fileId}
                  page={basePage}
                  zoom={state.viewSettings?.zoom || 1}
                  highlightMode="none"
                  differences={[]}
                  selectedDifference={null}
                  onDifferenceSelect={() => {}}
                  loading={loading}
                  interactive={false}
                  onImageLoaded={handleBaseImageLoaded}
                />
              )}
            </div>
            
            {/* Compare document layer with opacity */}
            <div 
              className="compare-layer" 
              style={{ 
                position: 'absolute', 
                top: 0, 
                left: 0, 
                width: '100%', 
                height: '100%',
                opacity: overlayOpacity,
                zIndex: 20,
                mixBlendMode: 'difference'
              }}
            >
              {comparePageExists && (
                <PDFRenderer 
                  fileId={state.compareFile?.fileId}
                  page={comparePage}
                  zoom={state.viewSettings?.zoom || 1}
                  highlightMode="none"
                  differences={[]}
                  selectedDifference={null}
                  onDifferenceSelect={() => {}}
                  loading={loading}
                  interactive={false}
                  onImageLoaded={handleCompareImageLoaded}
                />
              )}
            </div>
            
            {/* Highlight differences */}
            {pageDetails && (
              <div className="difference-overlays" style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                zIndex: 30,
                pointerEvents: 'none'
              }}>
                {pageDetails.baseDifferences && pageDetails.baseDifferences.map(diff => (
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
                      cursor: 'pointer',
                      zIndex: 50,
                      pointerEvents: 'auto'
                    }}
                    onClick={() => handleDifferenceSelect(diff)}
                  />
                ))}
              </div>
            )}
          </div>

          {/* Loading indicator */}
          {(!baseLoaded || !compareLoaded) && !loading && (
            <div style={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              transform: 'translate(-50%, -50%)',
              textAlign: 'center',
              padding: '20px',
              background: 'rgba(255, 255, 255, 0.8)',
              borderRadius: '8px',
              zIndex: 100
            }}>
              <div className="spinner" style={{
                width: '40px',
                height: '40px',
                margin: '0 auto 20px',
                border: '4px solid rgba(0, 0, 0, 0.1)',
                borderRadius: '50%',
                borderTopColor: '#2c6dbd',
                animation: 'spin 1s ease-in-out infinite'
              }}></div>
              <p>Loading PDF documents for overlay view...</p>
            </div>
          )}

          {/* No differences message */}
          {baseLoaded && compareLoaded && pageDetails && 
           pageDetails.baseDifferences && pageDetails.baseDifferences.length === 0 && (
            <div style={{
              position: 'absolute',
              top: '20px',
              left: '50%',
              transform: 'translateX(-50%)',
              padding: '10px 20px',
              background: 'rgba(255, 255, 255, 0.9)',
              borderRadius: '4px',
              border: '1px solid #2c6dbd',
              zIndex: 100,
              boxShadow: '0 2px 10px rgba(0, 0, 0, 0.1)'
            }}>
              <p style={{ margin: 0 }}>No differences highlighted on this page.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default OverlayPanel;