import React, { useState, useEffect } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { AlertCircle, RefreshCw, Clock, FileText, Download } from 'lucide-react';

// Performance dashboard to display alongside PDF comparison loading screen
const PerformanceDashboard = ({ comparisonId }) => {
  const [metrics, setMetrics] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [view, setView] = useState('operation'); // 'operation' or 'size'
  
  const fetchMetrics = async () => {
    setLoading(true);
    try {
      // If we have a comparisonId, we'll fetch metrics for that specific comparison
      const endpoint = comparisonId 
        ? `/api/diagnostics/performance/${comparisonId}` 
        : '/api/diagnostics/performance';
        
      const response = await fetch(endpoint);
      if (!response.ok) {
        throw new Error(`Error fetching metrics: ${response.statusText}`);
      }
      const data = await response.json();
      setMetrics(data.metrics || []);
      setLastUpdated(new Date());
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };
  
  // Format metrics for the operation-focused view
  const getOperationMetrics = () => {
    // Group by operation type
    const operationGroups = metrics.reduce((acc, metric) => {
      const { operationType, totalTimeMs } = metric;
      acc[operationType] = (acc[operationType] || 0) + totalTimeMs;
      return acc;
    }, {});
    
    // Convert to array for chart
    return Object.entries(operationGroups).map(([name, value]) => ({
      name: name.split('.').pop(), // Get the last part of the operation name
      value: Math.round(value)
    })).sort((a, b) => b.value - a.value);
  };
  
  // Format metrics for the size-focused view
  const getSizeMetrics = () => {
    // Group by size category
    const sizeGroups = metrics.reduce((acc, metric) => {
      const { sizeCategory, totalTimeMs } = metric;
      if (!sizeCategory) return acc; // Skip if no size category
      
      acc[sizeCategory] = (acc[sizeCategory] || 0) + totalTimeMs;
      return acc;
    }, {});
    
    // Convert to array for chart
    return Object.entries(sizeGroups).map(([name, value]) => ({
      name,
      value: Math.round(value)
    })).sort((a, b) => {
      // Sort by a logical size order
      const sizeOrder = { tiny: 0, small: 1, medium: 2, large: 3, very_large: 4, unknown: 5 };
      return sizeOrder[a.name] - sizeOrder[b.name];
    });
  };
  
  // Get summary statistics
  const getSummaryStats = () => {
    if (metrics.length === 0) return null;
    
    // Calculate total time across all operations
    const totalTime = metrics.reduce((sum, metric) => sum + metric.totalTimeMs, 0);
    
    // Get the slowest operation
    const slowestOp = metrics.sort((a, b) => b.maxTimeMs - a.maxTimeMs)[0];
    
    // Count operations by category
    const opCategories = metrics.reduce((acc, metric) => {
      const category = metric.operationType.split('.')[0];
      acc[category] = (acc[category] || 0) + 1;
      return acc;
    }, {});
    
    return {
      totalTime: Math.round(totalTime),
      totalOperations: metrics.length,
      slowestOperation: slowestOp ? {
        name: slowestOp.operationType.split('.').pop(),
        time: Math.round(slowestOp.maxTimeMs)
      } : null,
      categories: opCategories
    };
  };
  
  useEffect(() => {
    fetchMetrics();
    
    // Refresh every 5 seconds while viewing the loading screen
    const interval = setInterval(fetchMetrics, 5000);
    return () => clearInterval(interval);
  }, [comparisonId]);
  
  // Get formatted metrics data based on current view
  const chartData = view === 'operation' ? getOperationMetrics() : getSizeMetrics();
  const summaryStats = getSummaryStats();
  
  // Custom tooltip for the chart
  const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-white p-2 border border-gray-200 shadow-md rounded">
          <p className="font-medium">{label}</p>
          <p className="text-blue-600">{`Time: ${payload[0].value} ms`}</p>
        </div>
      );
    }
    return null;
  };
  
  return (
    <div className="w-full max-w-md mx-auto p-4 bg-white bg-opacity-90 rounded-lg shadow">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-lg font-bold text-gray-800">Performance Metrics</h2>
        <button 
          onClick={fetchMetrics}
          className="flex items-center px-2 py-1 text-sm bg-blue-500 text-white rounded hover:bg-blue-600"
          disabled={loading}
        >
          <RefreshCw className="w-3 h-3 mr-1" />
          Refresh
        </button>
      </div>
      
      {/* Last updated info */}
      <div className="flex items-center mb-3 text-xs text-gray-600">
        <Clock className="w-3 h-3 mr-1" />
        <span>Last updated: {lastUpdated ? lastUpdated.toLocaleTimeString() : 'Never'}</span>
      </div>
      
      {/* Error alert */}
      {error && (
        <div className="p-2 mb-3 text-sm bg-red-100 text-red-800 rounded-md flex items-start">
          <AlertCircle className="w-4 h-4 mr-1 flex-shrink-0 mt-0.5" />
          <span>{error}</span>
        </div>
      )}
      
      {/* Loading indicator */}
      {loading && (
        <div className="text-center p-2">
          <div className="inline-block animate-spin rounded-full h-5 w-5 border-t-2 border-b-2 border-blue-500"></div>
          <p className="mt-1 text-xs text-gray-600">Loading metrics...</p>
        </div>
      )}
      
      {/* Summary stats */}
      {summaryStats && !loading && (
        <div className="grid grid-cols-2 gap-2 mb-4">
          <div className="bg-blue-50 p-2 rounded">
            <div className="text-xs text-gray-500">Total Processing Time</div>
            <div className="text-lg font-bold">{(summaryStats.totalTime / 1000).toFixed(1)}s</div>
          </div>
          <div className="bg-green-50 p-2 rounded">
            <div className="text-xs text-gray-500">Operations</div>
            <div className="text-lg font-bold">{summaryStats.totalOperations}</div>
          </div>
          {summaryStats.slowestOperation && (
            <div className="col-span-2 bg-yellow-50 p-2 rounded">
              <div className="text-xs text-gray-500">Slowest Operation</div>
              <div className="text-sm font-medium">{summaryStats.slowestOperation.name}</div>
              <div className="text-xs text-gray-600">{summaryStats.slowestOperation.time} ms</div>
            </div>
          )}
        </div>
      )}
      
      {/* View toggle */}
      <div className="flex justify-center mb-3">
        <div className="bg-gray-100 rounded-lg p-1 flex text-sm">
          <button
            className={`px-3 py-1 rounded-md ${view === 'operation' ? 'bg-white shadow' : ''}`}
            onClick={() => setView('operation')}
          >
            By Operation
          </button>
          <button
            className={`px-3 py-1 rounded-md ${view === 'size' ? 'bg-white shadow' : ''}`}
            onClick={() => setView('size')}
          >
            By Size
          </button>
        </div>
      </div>
      
      {/* Chart */}
      {chartData.length > 0 && !loading ? (
        <div className="h-48 mt-2">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" tick={{ fontSize: 10 }} />
              <YAxis width={40} tickFormatter={(value) => `${value}ms`} tick={{ fontSize: 10 }} />
              <Tooltip content={<CustomTooltip />} />
              <Bar dataKey="value" name="Time (ms)" fill="#3B82F6" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : !loading && (
        <div className="text-center p-4 text-gray-500">
          No performance data available
        </div>
      )}
      
      <div className="mt-3 text-xs text-gray-500">
        <p>This data helps explain why PDF comparison may take time.</p>
      </div>
    </div>
  );
};

export default PerformanceDashboard;