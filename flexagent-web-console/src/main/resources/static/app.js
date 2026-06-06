const chatContainer = document.getElementById('chat-container');
const messageInput = document.getElementById('message-input');
const sendButton = document.getElementById('send-button');

// Auto-resize textarea
messageInput.addEventListener('input', function() {
    this.style.height = 'auto';
    this.style.height = (this.scrollHeight < 150 ? this.scrollHeight : 150) + 'px';
    sendButton.disabled = this.value.trim().length === 0;
});

messageInput.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        if (this.value.trim().length > 0) {
            sendMessage();
        }
    }
});

function createMessageElement(role) {
    const msgDiv = document.createElement('div');
    msgDiv.className = `message ${role}`;
    
    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.textContent = role === 'user' ? 'U' : '🤖';
    
    const content = document.createElement('div');
    content.className = 'content';
    
    msgDiv.appendChild(avatar);
    msgDiv.appendChild(content);
    return { msgDiv, content };
}

function createThinkingBlock() {
    const container = document.createElement('div');
    container.className = 'thinking-container';
    
    const header = document.createElement('div');
    header.className = 'thinking-header';
    header.innerHTML = `
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
        <span>Deep Thinking...</span>
        <svg class="toggle-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" style="margin-left: auto; transition: transform 0.2s;"><polyline points="6 9 12 15 18 9"></polyline></svg>
    `;
    
    const content = document.createElement('div');
    content.className = 'thinking-content';
    
    header.addEventListener('click', () => {
        content.classList.toggle('collapsed');
        const icon = header.querySelector('.toggle-icon');
        icon.style.transform = content.classList.contains('collapsed') ? 'rotate(-90deg)' : 'rotate(0deg)';
    });
    
    container.appendChild(header);
    container.appendChild(content);
    return { container, content, headerTitle: header.querySelector('span') };
}

async function sendMessage() {
    const text = messageInput.value.trim();
    if (!text) return;
    
    messageInput.value = '';
    messageInput.style.height = 'auto';
    sendButton.disabled = true;
    
    // Add user message
    const { msgDiv: userMsg, content: userContent } = createMessageElement('user');
    userContent.textContent = text;
    chatContainer.appendChild(userMsg);
    chatContainer.scrollTop = chatContainer.scrollHeight;
    
    // Add AI placeholder message
    const { msgDiv: botMsg, content: botContent } = createMessageElement('system');
    
    let thinkingBlock = null;
    let textContainer = document.createElement('div');
    textContainer.className = 'markdown-body';
    
    botContent.appendChild(textContainer);
    
    const cursor = document.createElement('span');
    cursor.className = 'cursor';
    botContent.appendChild(cursor);
    
    chatContainer.appendChild(botMsg);
    chatContainer.scrollTop = chatContainer.scrollHeight;
    
    try {
        // We use fetch API and read the stream manually since SSE (EventSource) doesn't easily support POST with body
        const response = await fetch('/api/chat/stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'text/event-stream'
            },
            body: JSON.stringify({ message: text })
        });
        
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        
        let rawText = "";
        let rawThinking = "";
        let isDone = false;
        
        while (!isDone) {
            const { value, done } = await reader.read();
            if (done) break;
            
            const chunk = decoder.decode(value, { stream: true });
            const lines = chunk.split('\n');
            
            for (let line of lines) {
                if (line.startsWith('data:')) {
                    const dataStr = line.substring(5).trim();
                    if (!dataStr) continue;
                    
                    try {
                        const step = JSON.parse(dataStr);
                        
                        if (step.type === 'TOOL_CALL') {
                            const toolBadge = document.createElement('div');
                            toolBadge.className = 'tool-call';
                            toolBadge.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"></path></svg> Calling tool...`;
                            botContent.insertBefore(toolBadge, textContainer);
                        } 
                        else if (step.type === 'TOOL_CALL' && step.status === 'SUCCESS' && step.content === 'TOOL_DONE') {
                            // tool finished
                            const badges = botContent.querySelectorAll('.tool-call');
                            if (badges.length > 0) {
                                badges[badges.length - 1].innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="20 6 9 17 4 12"></polyline></svg> Tool returned results`;
                                badges[badges.length - 1].style.color = '#10b981';
                                badges[badges.length - 1].style.background = 'rgba(16, 185, 129, 0.1)';
                                badges[badges.length - 1].style.borderColor = 'rgba(16, 185, 129, 0.2)';
                            }
                        }
                        else if (step.type === 'STREAM_TOKEN') {
                            // This depends on how the backend emits. The backend we wrote emits STREAM_TOKEN with content containing <think> tags.
                            // We will just accumulate raw content and split by <think> locally for simplicity.
                            const token = step.content;
                            rawText += token;
                            
                            // Parse <think> locally
                            const thinkMatch = rawText.match(/<think>([\s\S]*?)<\/think>/);
                            const openThinkMatch = rawText.match(/<think>([\s\S]*)$/);
                            
                            let visibleText = rawText;
                            
                            if (thinkMatch) {
                                rawThinking = thinkMatch[1];
                                visibleText = rawText.replace(thinkMatch[0], '');
                            } else if (openThinkMatch) {
                                rawThinking = openThinkMatch[1];
                                visibleText = rawText.substring(0, openThinkMatch.index);
                            }
                            
                            if (rawThinking) {
                                if (!thinkingBlock) {
                                    thinkingBlock = createThinkingBlock();
                                    botContent.insertBefore(thinkingBlock.container, textContainer);
                                }
                                thinkingBlock.content.textContent = rawThinking;
                            }
                            
                            textContainer.innerHTML = marked.parse(visibleText);
                            chatContainer.scrollTop = chatContainer.scrollHeight;
                            
                        } else if (step.type === 'TEXT_RESPONSE' || step.type === 'ERROR') {
                            isDone = true;
                            if (step.type === 'ERROR') {
                                textContainer.innerHTML += `<br><span style="color: #ef4444;">Error: ${step.content}</span>`;
                            }
                        }
                    } catch (e) {
                        console.error("Parse error", e);
                    }
                }
            }
        }
        
        // Finalize
        cursor.remove();
        if (thinkingBlock) {
            thinkingBlock.headerTitle.textContent = "Thought Process";
            thinkingBlock.content.classList.add('collapsed'); // Auto collapse when done
            const icon = thinkingBlock.container.querySelector('.toggle-icon');
            icon.style.transform = 'rotate(-90deg)';
        }
        
    } catch (e) {
        cursor.remove();
        textContainer.innerHTML += `<br><span style="color: #ef4444;">Connection failed.</span>`;
    }
}
