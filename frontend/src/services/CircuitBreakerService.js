// src/services/CircuitBreakerService.js
class CircuitBreakerService {
    constructor(options = {}) {
      this.state = {
        failures: 0,
        lastFailureTime: 0,
        isOpen: false,
        halfOpenSuccess: false
      };
      
      this.config = {
        threshold: options.threshold || 5,
        resetTimeout: options.resetTimeout || 30000, // Default 30s
        halfOpenRetryCount: options.halfOpenRetryCount || 1,
        maxConsecutiveHalfOpenFailures: options.maxConsecutiveHalfOpenFailures || 3
      };
      
      this.debug = options.debug || false;
      this.consecutiveHalfOpenFailures = 0;
      this.observers = [];
    }
  
    // Add observers for state changes
    addObserver(callback) {
      if (typeof callback === 'function') {
        this.observers.push(callback);
      }
      return this;
    }
  
    // Notify all observers
    notifyObservers() {
      for (const observer of this.observers) {
        observer(this.getStatus());
      }
    }
  
    // Get current circuit status
    getStatus() {
      return {
        isOpen: this.state.isOpen,
        failures: this.state.failures,
        lastFailureTime: this.state.lastFailureTime,
        timeSinceLastFailure: Date.now() - this.state.lastFailureTime,
        resetTimeout: this.config.resetTimeout,
        halfOpenSuccess: this.state.halfOpenSuccess,
        threshold: this.config.threshold
      };
    }
  
    // Check if the circuit is closed (allowing requests)
    isClosed() {
      return !this.isOpen();
    }
  
    // Check if the circuit is open (blocking requests)
    isOpen() {
      if (!this.state.isOpen) {
        return false;
      }
  
      // Check if enough time has passed to try half-open state
      const now = Date.now();
      const timeSinceLastFailure = now - this.state.lastFailureTime;
      
      if (timeSinceLastFailure > this.config.resetTimeout) {
        if (this.debug) {
          console.log('Circuit breaker: Transitioning to half-open state to test if service is back');
        }
        
        // We're in half-open state
        return false;
      }
      
      if (this.debug) {
        console.log(`Circuit breaker: Open (${Math.round(timeSinceLastFailure / 1000)}s since last failure, will retry after ${Math.round((this.config.resetTimeout - timeSinceLastFailure) / 1000)}s)`);
      }
      
      return true;
    }
  
    // Record a successful request
    success() {
      if (this.state.isOpen) {
        // We were in half-open state and the request succeeded
        if (this.debug) {
          console.log('Circuit breaker: Half-open request succeeded, closing circuit');
        }
        
        this.state.isOpen = false;
        this.state.failures = 0;
        this.state.halfOpenSuccess = true;
        this.consecutiveHalfOpenFailures = 0;
      } else if (this.state.failures > 0) {
        // Reset failure count after success
        if (this.debug) {
          console.log('Circuit breaker: Resetting failure count after success');
        }
        
        this.state.failures = 0;
      }
      
      this.notifyObservers();
      return this;
    }
  
    // Record a failed request
    failure() {
      if (this.state.isOpen) {
        // Failed during half-open state, reopen the circuit fully
        this.consecutiveHalfOpenFailures++;
        
        if (this.debug) {
          console.log(`Circuit breaker: Half-open request failed (${this.consecutiveHalfOpenFailures}/${this.config.maxConsecutiveHalfOpenFailures})`);
        }
        
        // If we've had too many consecutive half-open failures,
        // increase the reset timeout to avoid hammering the service
        if (this.consecutiveHalfOpenFailures >= this.config.maxConsecutiveHalfOpenFailures) {
          this.config.resetTimeout = Math.min(this.config.resetTimeout * 2, 300000); // Max 5 minutes
          
          if (this.debug) {
            console.log(`Circuit breaker: Increasing reset timeout to ${this.config.resetTimeout}ms due to repeated failures`);
          }
          
          this.consecutiveHalfOpenFailures = 0;
        }
      } else {
        // Normal failure, increment counter
        this.state.failures++;
        
        if (this.debug) {
          console.log(`Circuit breaker: Failure count increased to ${this.state.failures}/${this.config.threshold}`);
        }
        
        // Open the circuit if we've hit the threshold
        if (this.state.failures >= this.config.threshold) {
          if (this.debug) {
            console.log('Circuit breaker: Opening circuit due to too many failures');
          }
          
          this.state.isOpen = true;
          this.state.halfOpenSuccess = false;
        }
      }
      
      this.state.lastFailureTime = Date.now();
      this.notifyObservers();
      return this;
    }
  
    // Reset the circuit breaker to initial state
    reset() {
      this.state.failures = 0;
      this.state.lastFailureTime = 0;
      this.state.isOpen = false;
      this.state.halfOpenSuccess = false;
      this.consecutiveHalfOpenFailures = 0;
      this.config.resetTimeout = this.config.resetTimeout; // Reset to initial value
      
      if (this.debug) {
        console.log('Circuit breaker: Reset to initial state');
      }
      
      this.notifyObservers();
      return this;
    }
  }
  
  export default CircuitBreakerService;