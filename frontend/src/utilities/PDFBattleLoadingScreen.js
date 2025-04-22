import React, { useState, useEffect } from 'react';
import PerformanceDashboard from './PerformanceDashboard'; // Import the dashboard component

    const DoctechtiveLoadingScreen = ({ progress = 0, currentPhase = "Initializing", comparisonId }) => {
      const [messageIndex, setMessageIndex] = useState(0);
      const [reducedMotion, setReducedMotion] = useState(false);
      const [showMetrics, setShowMetrics] = useState(true); // Default to showing the metrics panel
      
      // Interrogation messages
      const interrogationMessages = [
        "YOUR FONTS DON'T MATCH UP!",
        "WHERE IS PAGE 5 CONTENT?",
        "THOSE MARGINS ARE SUSPICIOUS!",
        "CONFESS YOUR FORMAT CHANGES!",
        "I KNOW PAGES WERE REMOVED!",
        "WHAT HAPPENED TO THAT IMAGE?",
        "EXPLAIN THESE DIFFERENCES!",
        "YOUR VERSIONS DON'T ALIGN!",
      ];
      
      // Progress phases with more detailed descriptions
      const phaseDescriptions = {
        "Initializing": "Opening case files...",
        "Analyzing": "Gathering evidence...",
        "Comparing Text": "Interrogating text content...",
        "Processing Images": "Examining visual evidence...",
        "Checking Fonts": "Analyzing typographic fingerprints...",
        "Finalizing": "Building the case file...",
      };
    
      // Cycle through interrogation messages
      React.useEffect(() => {
        if (reducedMotion) return;
        
        const interval = setInterval(() => {
          setMessageIndex((prev) => (prev + 1) % interrogationMessages.length);
        }, 3000);
        
        return () => clearInterval(interval);
      }, [reducedMotion, interrogationMessages.length]);
    
      // Handle reduced motion toggle
      const toggleReducedMotion = () => {
        setReducedMotion(!reducedMotion);
      };
    
      // Get current phase description
      const getPhaseDescription = () => {
        return phaseDescriptions[currentPhase] || currentPhase;
      };
      
      // Toggle metrics panel visibility
      const toggleMetrics = () => {
        setShowMetrics(!showMetrics);
      };
      
      // Animations as keyframes
      const keyframes = `
        @keyframes point {
          0%, 100% { transform: rotate(0deg); }
          50% { transform: rotate(15deg) translateY(-8px); }
        }
        
        @keyframes tap-foot {
          0%, 100% { transform: rotate(0deg); }
          50% { transform: rotate(20deg); }
        }
        
        @keyframes shake-fear {
          0%, 100% { transform: translateX(0) rotate(0); }
          25% { transform: translateX(-5px) rotate(-1deg); }
          75% { transform: translateX(5px) rotate(1deg); }
        }
        
        @keyframes blink {
          0%, 100% { transform: scaleY(1); }
          50% { transform: scaleY(0.1); }
        }
        
        @keyframes sweat {
          0% { opacity: 0; transform: translateY(0); }
          30% { opacity: 1; }
          100% { transform: translateY(30px); opacity: 0; }
        }
        
        @keyframes lamp-flicker {
          0%, 100% { opacity: 0.7; }
          50% { opacity: 1; }
        }
        
        @keyframes pulsate {
          0%, 100% { transform: scale(1); }
          50% { transform: scale(1.05); }
        }
        
        @keyframes spin-slow {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
        
        @keyframes fade-in-out {
          0%, 100% { opacity: 0.8; }
          50% { opacity: 0.5; }
        }
      `;
    
      return (
        <div style={{
          width: '100%',
          maxWidth: '1200px',
          minWidth: '320px',
          margin: '0 auto',
          fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
        }}>
          <style>{keyframes}</style>
          
          {/* Main container with responsive layout */}
          <div style={{
            display: 'flex',
            flexDirection: window.innerWidth < 1024 ? 'column' : 'row',
            gap: '20px',
            padding: '10px'
          }}>
            {/* Left side - Interrogation animation */}
            <div style={{
              flex: '1 1 60%'
            }}>
              {/* Accessibility control */}
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                marginBottom: '10px'
              }}>
                <button 
                  onClick={toggleReducedMotion} 
                  style={{
                    backgroundColor: '#374151',
                    color: 'white',
                    border: 'none',
                    borderRadius: '4px',
                    padding: '8px 16px',
                    fontSize: '14px',
                    cursor: 'pointer'
                  }}
                >
                  {reducedMotion ? 'Enable' : 'Reduce'} Motion
                </button>
                
                <button 
                  onClick={toggleMetrics} 
                  style={{
                    backgroundColor: '#2c6dbd',
                    color: 'white',
                    border: 'none',
                    borderRadius: '4px',
                    padding: '8px 16px',
                    fontSize: '14px',
                    cursor: 'pointer'
                  }}
                >
                  {showMetrics ? 'Hide' : 'Show'} Performance Metrics
                </button>
              </div>
              
              {/* Interrogation room scene */}
              <div style={{
                position: 'relative',
                height: '600px',
                backgroundColor: '#1a1a1a',
                backgroundImage: 'radial-gradient(circle at 50% 50%, #2a2a2a 0%, #111 100%)',
                border: '4px solid #2c6dbd',
                borderRadius: '12px',
                boxShadow: '0 20px 40px rgba(0, 0, 0, 0.5)',
                overflow: 'hidden',
                marginBottom: '30px'
              }}>
                {/* Circuit board background pattern */}
                <div style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  right: 0,
                  bottom: 0,
                  opacity: 0.07,
                  backgroundImage: `
                    linear-gradient(90deg, rgba(100, 200, 255, 0.3) 1px, transparent 1px),
                    linear-gradient(rgba(100, 200, 255, 0.3) 1px, transparent 1px)
                  `,
                  backgroundSize: '30px 30px'
                }} />
                
                {/* Interrogation table */}
                <div style={{
                  position: 'absolute',
                  bottom: '100px',
                  left: '50%',
                  transform: 'translateX(-50%)',
                  width: '600px',
                  height: '40px',
                  backgroundColor: '#444',
                  borderRadius: '8px',
                  boxShadow: '0 10px 20px rgba(0, 0, 0, 0.5)'
                }} />
                
                {/* Lamp */}
                <div style={{
                  position: 'absolute',
                  top: '80px',
                  left: '50%',
                  transform: 'translateX(-50%)',
                  zIndex: 3
                }}>
                  {/* Lamp fixture */}
                  <div style={{
                    width: '16px',
                    height: '100px',
                    backgroundColor: '#333',
                    margin: '0 auto'
                  }} />
                  <div style={{
                    width: '60px',
                    height: '35px',
                    backgroundColor: '#333',
                    borderRadius: '60px 60px 0 0',
                    margin: '0 auto'
                  }} />
                  
                  {/* Lamp cone */}
                  <div style={{
                    position: 'relative',
                    width: '100px',
                    height: '50px',
                    backgroundColor: '#444',
                    borderRadius: '100px 100px 0 0',
                    marginTop: '-2px',
                    boxShadow: '0 5px 10px rgba(0, 0, 0, 0.5)'
                  }} />
                  
                  {/* Light beam */}
                  <div style={{
                    position: 'absolute',
                    top: '148px',
                    left: '50%',
                    transform: 'translateX(-50%)',
                    width: '400px',
                    height: '450px',
                    background: 'radial-gradient(ellipse at top, rgba(255, 255, 200, 0.4), transparent 70%)',
                    animation: reducedMotion ? 'none' : 'lamp-flicker 2s infinite',
                    zIndex: 1
                  }} />
                </div>
                
                {/* PDF A - Detective */}
                <div style={{
                  position: 'absolute',
                  bottom: '140px',
                  left: '180px',
                  zIndex: 4
                }}>
                  {/* PDF Body */}
                  <div style={{
                    position: 'relative',
                    width: '120px',
                    height: '170px',
                    backgroundColor: '#2c6dbd',
                    borderRadius: '12px',
                    boxShadow: '5px 5px 15px rgba(0, 0, 0, 0.5)',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    alignItems: 'center'
                  }}>
                    {/* PDF Lines */}
                    <div style={{
                      width: '85px',
                      height: '10px',
                      backgroundColor: 'white',
                      borderRadius: '3px',
                      margin: '6px 0'
                    }} />
                    <div style={{
                      width: '85px',
                      height: '10px',
                      backgroundColor: 'white',
                      borderRadius: '3px',
                      margin: '6px 0'
                    }} />
                    <div style={{
                      width: '85px',
                      height: '10px',
                      backgroundColor: 'white',
                      borderRadius: '3px',
                      margin: '6px 0'
                    }} />
                    
                    {/* Face */}
                    <div style={{
                      position: 'absolute',
                      top: '45px',
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center'
                    }}>
                      {/* Eyes */}
                      <div style={{
                        display: 'flex',
                        gap: '25px',
                        marginBottom: '10px'
                      }}>
                        <div style={{
                          width: '14px',
                          height: '14px',
                          backgroundColor: 'black',
                          borderRadius: '50%',
                          animation: reducedMotion ? 'none' : 'blink 4s infinite'
                        }} />
                        <div style={{
                          width: '14px',
                          height: '14px',
                          backgroundColor: 'black',
                          borderRadius: '50%',
                          animation: reducedMotion ? 'none' : 'blink 4s infinite'
                        }} />
                      </div>
                      
                      {/* Mouth */}
                      <div style={{
                        width: '35px',
                        height: '7px',
                        backgroundColor: 'black',
                        borderRadius: '4px',
                        marginTop: '18px'
                      }} />
                    </div>
                    
                    {/* Badge */}
                    <div style={{
                      position: 'absolute',
                      bottom: '20px',
                      width: '35px',
                      height: '35px',
                      backgroundColor: '#ffd700',
                      borderRadius: '50%',
                      display: 'flex',
                      justifyContent: 'center',
                      alignItems: 'center',
                      fontSize: '14px',
                      fontWeight: 'bold',
                      color: '#00008b'
                    }}>
                      PDF
                    </div>
                  </div>
                  
                  {/* Arms */}
                  <div style={{
                    position: 'absolute',
                    top: '60px',
                    left: '-35px',
                    width: '45px',
                    height: '14px',
                    backgroundColor: '#e63946',
                    borderRadius: '7px',
                    transform: 'rotate(-20deg)'
                  }} />
                  <div style={{
                    position: 'absolute',
                    top: '60px',
                    right: '-35px',
                    width: '45px',
                    height: '14px',
                    backgroundColor: '#e63946',
                    borderRadius: '7px',
                    transform: 'rotate(20deg)'
                  }} />
                  
                  {/* Handcuffs */}
                  <div style={{
                    position: 'absolute',
                    top: '55px',
                    right: '-40px',
                    width: '12px',
                    height: '20px',
                    borderRadius: '6px',
                    border: '3px solid #aaa',
                    borderRight: 'none'
                  }} />
                  <div style={{
                    position: 'absolute',
                    top: '55px',
                    right: '-60px',
                    width: '12px',
                    height: '20px',
                    borderRadius: '6px',
                    border: '3px solid #aaa',
                    borderLeft: 'none'
                  }} />
                  <div style={{
                    position: 'absolute',
                    top: '65px',
                    right: '-50px',
                    width: '18px',
                    height: '3px',
                    backgroundColor: '#aaa'
                  }} />
                  
                  {/* Legs */}
                  <div style={{
                    position: 'absolute',
                    bottom: '-30px',
                    left: '35px',
                    width: '14px',
                    height: '40px',
                    backgroundColor: '#e63946',
                    borderRadius: '0 0 7px 7px',
                    transform: 'rotate(45deg)',
                    transformOrigin: 'top'
                  }} />
                  <div style={{
                    position: 'absolute',
                    bottom: '-30px',
                    right: '35px',
                    width: '14px',
                    height: '40px',
                    backgroundColor: '#e63946',
                    borderRadius: '0 0 7px 7px',
                    transform: 'rotate(-45deg)',
                    transformOrigin: 'top'
                  }} />
                  
                  {/* Sweat drops */}
                  {!reducedMotion && (
                    <>
                      <div style={{
                        position: 'absolute',
                        top: '35px',
                        right: '-14px',
                        width: '10px',
                        height: '16px',
                        backgroundColor: '#3B82F6',
                        borderRadius: '50%',
                        opacity: 0.7,
                        animation: 'sweat 2s infinite'
                      }} />
                      <div style={{
                        position: 'absolute',
                        top: '65px',
                        right: '-18px',
                        width: '10px',
                        height: '16px',
                        backgroundColor: '#3B82F6',
                        borderRadius: '50%',
                        opacity: 0.7,
                        animation: 'sweat 2.5s infinite 0.5s'
                      }} />
                      <div style={{
                        position: 'absolute',
                        top: '95px',
                        right: '-12px',
                        width: '10px',
                        height: '16px',
                        backgroundColor: '#3B82F6',
                        borderRadius: '50%',
                        opacity: 0.7,
                        animation: 'sweat 2.2s infinite 1s'
                      }} />
                    </>
                  )}
                  {/* Police Hat */}
                  <div style={{
                    position: 'absolute',
                    top: '-40px',
                    left: '10px',
                    width: '100px',
                    height: '40px'
                  }}>
                    <div style={{
                      width: '100px',
                      height: '25px',
                      backgroundColor: '#00008b',
                      borderRadius: '8px 8px 0 0'
                    }} />
                    <div style={{
                      width: '120px',
                      height: '15px',
                      backgroundColor: '#00008b',
                      borderRadius: '5px',
                      position: 'absolute',
                      bottom: '0',
                      left: '-10px'
                    }} />
                    <div style={{
                      position: 'absolute',
                      top: '5px',
                      left: '35px',
                      width: '30px',
                      height: '12px',
                      borderRadius: '6px',
                      backgroundColor: '#ffd700'
                    }} />
                  </div>
                  
                  {/* Arms */}
                  <div style={{
                    position: 'absolute',
                    top: '60px',
                    right: '-35px',
                    width: '45px',
                    height: '14px',
                    backgroundColor: '#2c6dbd',
                    borderRadius: '7px',
                    transformOrigin: '0 50%',
                    animation: reducedMotion ? 'none' : 'point 2s infinite',
                    zIndex: 1
                  }} />
                  <div style={{
                    position: 'absolute',
                    top: '80px',
                    left: '-35px',
                    width: '45px',
                    height: '14px',
                    backgroundColor: '#2c6dbd',
                    borderRadius: '7px'
                  }} />
                  
                  {/* Legs */}
                  <div style={{
                    position: 'absolute',
                    bottom: '-40px',
                    left: '30px',
                    width: '14px',
                    height: '45px',
                    backgroundColor: '#2c6dbd',
                    borderRadius: '0 0 7px 7px'
                  }} />
                  <div style={{
                    position: 'absolute',
                    bottom: '-40px',
                    right: '30px',
                    width: '14px',
                    height: '45px',
                    backgroundColor: '#2c6dbd',
                    borderRadius: '0 0 7px 7px',
                    transformOrigin: 'top',
                    animation: reducedMotion ? 'none' : 'tap-foot 1s infinite'
                  }} />
                  
                  {/* Speech bubble */}
                  <div style={{
                    position: 'absolute',
                    top: '-70px',
                    left: '120px',
                    backgroundColor: 'white',
                    padding: '15px',
                    borderRadius: '12px',
                    width: '240px',
                    maxWidth: '240px',
                    boxShadow: '0 8px 16px rgba(0, 0, 0, 0.2)',
                    zIndex: 10,
                    animation: reducedMotion ? 'none' : 'pulsate 2s infinite'
                  }}>
                    <div style={{
                      position: 'absolute',
                      left: '-20px',
                      top: '25px',
                      width: '0',
                      height: '0',
                      borderTop: '15px solid transparent',
                      borderRight: '25px solid white',
                      borderBottom: '15px solid transparent'
                    }} />
                    <p style={{
                      margin: 0,
                      fontWeight: 'bold',
                      fontSize: '18px',
                      textAlign: 'center',
                      color: '#00008b'
                    }}>
                      {interrogationMessages[messageIndex]}
                    </p>
                  </div>
                </div>
                
                {/* PDF B - Suspect */}
                <div style={{
                  position: 'absolute',
                  bottom: '140px',
                  right: '200px',
                  zIndex: 3,
                  animation: reducedMotion ? 'none' : 'shake-fear 0.5s infinite'
                }}>
                  {/* Chair */}
                  <div style={{
                    position: 'absolute',
                    bottom: '-25px',
                    left: '15px',
                    width: '90px',
                    height: '15px',
                    backgroundColor: '#555',
                    borderRadius: '4px'
                  }} />
                  <div style={{
                    position: 'absolute',
                    bottom: '-55px',
                    left: '25px',
                    width: '10px',
                    height: '30px',
                    backgroundColor: '#555'
                  }} />
                  <div style={{
                    position: 'absolute',
                    bottom: '-55px',
                    left: '85px',
                    width: '10px',
                    height: '30px',
                    backgroundColor: '#555'
                  }} />
                  
                  {/* PDF Body */}
                  <div style={{
                    position: 'relative',
                    width: '120px',
                    height: '170px',
                    backgroundColor: '#e63946',
                    borderRadius: '12px',
                    boxShadow: '5px 5px 15px rgba(0, 0, 0, 0.5)',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    alignItems: 'center'
                  }}>
                    {/* PDF Lines */}
                    <div style={{
                      width: '85px',
                      height: '10px',
                      backgroundColor: 'white',
                      borderRadius: '3px',
                      margin: '6px 0'
                    }} />
                    <div style={{
                      width: '85px',
                      height: '10px',
                      backgroundColor: 'white',
                      borderRadius: '3px',
                      margin: '6px 0'
                    }} />
                    <div style={{
                      width: '85px',
                      height: '10px',
                      backgroundColor: 'white',
                      borderRadius: '3px',
                      margin: '6px 0'
                    }} />
                    
                    {/* Face */}
                    <div style={{
                      position: 'absolute',
                      top: '45px',
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center'
                    }}>
                      {/* Eyes */}
                      <div style={{
                        display: 'flex',
                        gap: '25px',
                        marginBottom: '10px'
                      }}>
                        <div style={{
                          width: '16px',
                          height: '16px',
                          backgroundColor: 'black',
                          borderRadius: '50%',
                          position: 'relative'
                        }}>
                          <div style={{
                            position: 'absolute',
                            top: '3px',
                            right: '3px',
                            width: '5px',
                            height: '5px',
                            backgroundColor: 'white',
                            borderRadius: '50%'
                          }} />
                        </div>
                        <div style={{
                          width: '16px',
                          height: '16px',
                          backgroundColor: 'black',
                          borderRadius: '50%',
                          position: 'relative'
                        }}>
                          <div style={{
                            position: 'absolute',
                            top: '3px',
                            right: '3px',
                            width: '5px',
                            height: '5px',
                            backgroundColor: 'white',
                            borderRadius: '50%'
                          }} />
                        </div>
                      </div>
                      
                
             {/* Worried mouth */}
<div style={{
  width: '35px',
  height: '18px',
  border: '3px solid black',
  borderTop: 'none',
  borderRadius: '0 0 18px 18px',
  marginTop: '18px'
}} />
</div>
</div>

{/* Arms */}
<div style={{
  position: 'absolute',
  top: '60px',
  left: '-35px',
  width: '45px',
  height: '14px',
  backgroundColor: '#e63946',
  borderRadius: '7px',
  transform: 'rotate(-20deg)'
}} />
<div style={{
  position: 'absolute',
  top: '60px',
  right: '-35px',
  width: '45px',
  height: '14px',
  backgroundColor: '#e63946',
  borderRadius: '7px',
  transform: 'rotate(20deg)'
}} />

{/* Handcuffs */}
<div style={{
  position: 'absolute',
  top: '55px',
  right: '-40px',
  width: '12px',
  height: '20px',
  borderRadius: '6px',
  border: '3px solid #aaa',
  borderRight: 'none'
}} />
<div style={{
  position: 'absolute',
  top: '55px',
  right: '-60px',
  width: '12px',
  height: '20px',
  borderRadius: '6px',
  border: '3px solid #aaa',
  borderLeft: 'none'
}} />
<div style={{
  position: 'absolute',
  top: '65px',
  right: '-50px',
  width: '18px',
  height: '3px',
  backgroundColor: '#aaa'
}} />

{/* Legs */}
<div style={{
  position: 'absolute',
  bottom: '-30px',
  left: '35px',
  width: '14px',
  height: '40px',
  backgroundColor: '#e63946',
  borderRadius: '0 0 7px 7px',
  transform: 'rotate(45deg)',
  transformOrigin: 'top'
}} />
<div style={{
  position: 'absolute',
  bottom: '-30px',
  right: '35px',
  width: '14px',
  height: '40px',
  backgroundColor: '#e63946',
  borderRadius: '0 0 7px 7px',
  transform: 'rotate(-45deg)',
  transformOrigin: 'top'
}} />

{/* Sweat drops */}
{!reducedMotion && (
  <>
    <div style={{
      position: 'absolute',
      top: '35px',
      right: '-14px',
      width: '10px',
      height: '16px',
      backgroundColor: '#3B82F6',
      borderRadius: '50%',
      opacity: 0.7,
      animation: 'sweat 2s infinite'
    }} />
    <div style={{
      position: 'absolute',
      top: '65px',
      right: '-18px',
      width: '10px',
      height: '16px',
      backgroundColor: '#3B82F6',
      borderRadius: '50%',
      opacity: 0.7,
      animation: 'sweat 2.5s infinite 0.5s'
    }} />
    <div style={{
      position: 'absolute',
      top: '95px',
      right: '-12px',
      width: '10px',
      height: '16px',
      backgroundColor: '#3B82F6',
      borderRadius: '50%',
      opacity: 0.7,
      animation: 'sweat 2.2s infinite 1s'
    }} />
  </>
)}
</div>
</div>
</div>

{/* Right side - Performance metrics */}
{showMetrics && (
  <div style={{
    flex: '1 1 40%',
    marginTop: window.innerWidth < 1024 ? '0' : '50px'
  }}>
    <PerformanceDashboard comparisonId={comparisonId} />
  </div>
)}
</div>

{/* Progress bar section */}
<div style={{
  backgroundColor: '#1F2937',
  padding: '25px',
  borderRadius: '12px',
  boxShadow: '0 8px 16px rgba(0, 0, 0, 0.2)'
}}>
  <div style={{
    display: 'flex',
    justifyContent: 'space-between',
    marginBottom: '15px'
  }}>
    <div style={{
      color: '#60A5FA',
      fontSize: '18px',
      fontFamily: 'monospace'
    }}>
      {getPhaseDescription()}
    </div>
    <div style={{
      color: '#60A5FA',
      fontSize: '18px',
      fontFamily: 'monospace'
    }}>
      {Math.round(progress)}% complete
    </div>
  </div>
  
  <div style={{
    width: '100%',
    height: '16px',
    backgroundColor: '#374151',
    borderRadius: '8px',
    overflow: 'hidden'
  }}>
    <div style={{
      height: '100%',
      width: `${Math.min(progress, 100)}%`,
      background: 'linear-gradient(90deg, #2563EB 0%, #10B981 100%)',
      borderRadius: '8px',
      transition: 'width 0.5s ease-out'
    }} />
  </div>
  
  <div style={{
    textAlign: 'center',
    marginTop: '20px',
    color: '#9CA3AF',
    fontSize: '16px'
  }}>
    DocTechtive Case #{Math.floor(1000 + Math.random() * 9000)}-PDF
  </div>
</div>
</div>
    );
  };
  
  export default DoctechtiveLoadingScreen;