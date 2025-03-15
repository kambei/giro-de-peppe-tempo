// Simple implementation for demo purposes
console.log('Script loaded');

// Helper function to generate random IDs for trace and span
function generateRandomId(length) {
    const characters = '0123456789abcdef';
    let result = '';
    for (let i = 0; i < length; i++) {
        result += characters.charAt(Math.floor(Math.random() * characters.length));
    }
    return result;
}

// Wait for DOM to be loaded
document.addEventListener('DOMContentLoaded', () => {
    console.log('Frontend app initialized');
    
    // Add event listener for button click
    const button = document.getElementById('my-button');
    const resultElement = document.getElementById('result');
    
    console.log('Button element:', button);
    console.log('Result element:', resultElement);
    
    button.addEventListener('click', () => {
        console.log('User clicked the button.');
        
        // Show result
        resultElement.style.display = 'block';
        
        // Generate a random trace ID and span ID for demonstration
        const traceId = generateRandomId(32);
        const spanId = generateRandomId(16);
        
        resultElement.innerHTML = `
            <p>Trace generated at: ${new Date().toLocaleTimeString()}</p>
            <p>Trace ID: ${traceId}</p>
            <p>Span ID: ${spanId}</p>
        `;
        
        // Make a sample API call to demonstrate distributed tracing
        fetch('/api/hello', {
            headers: {
                'traceparent': `00-${traceId}-${spanId}-01`
            }
        })
        .then(response => {
            console.log('API response status:', response.status);
            if (!response.ok) {
                throw new Error(`HTTP error! Status: ${response.status}`);
            }
            return response.text();
        })
        .then(data => {
            console.log('API response:', data);
            resultElement.innerHTML += `<p>API Response: ${data}</p>`;
        })
        .catch(error => {
            console.error('API error:', error);
            resultElement.innerHTML += `<p>API Error: ${error.message}</p>`;
        });
    });
});
