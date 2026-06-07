require('dotenv').config();
const express = require('express');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());

// API Keys from environment (never expose to client)
const API_KEYS = {
  openai: process.env.OPENAI_API_KEY || '',
  claude: process.env.CLAUDE_API_KEY || '',
  deepseek: process.env.DEEPSEEK_API_KEY || '',
};

// MiMo configuration
const MIMO_CONFIG = {
  baseUrl: process.env.MIMO_BASE_URL || '',
  model: process.env.MIMO_MODEL || 'MiMo-7B-RL',
};

// Default provider
const DEFAULT_PROVIDER = process.env.DEFAULT_PROVIDER || 'openai';

/**
 * Health check endpoint
 */
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

/**
 * Get available providers (without exposing keys)
 */
app.get('/api/providers', (req, res) => {
  const providers = [];

  if (API_KEYS.openai) {
    providers.push({
      id: 'openai',
      name: MIMO_CONFIG.baseUrl ? 'Xiaomi MiMo' : 'OpenAI',
      model: MIMO_CONFIG.baseUrl ? MIMO_CONFIG.model : 'gpt-4o'
    });
  }
  if (API_KEYS.claude) providers.push({ id: 'claude', name: 'Claude', model: 'claude-sonnet-4-20250514' });
  if (API_KEYS.deepseek) providers.push({ id: 'deepseek', name: 'DeepSeek', model: 'deepseek-chat' });

  res.json({
    providers,
    default: DEFAULT_PROVIDER
  });
});

/**
 * Chat completion endpoint (proxies to AI providers)
 */
app.post('/api/chat', async (req, res) => {
  try {
    const { provider, messages, tools, systemPrompt, maxTokens = 4096 } = req.body;

    const selectedProvider = provider || DEFAULT_PROVIDER;
    const apiKey = API_KEYS[selectedProvider];

    if (!apiKey) {
      return res.status(400).json({
        error: `Provider '${selectedProvider}' not configured on server`
      });
    }

    let response;

    switch (selectedProvider) {
      case 'openai':
        response = await callOpenAI(apiKey, messages, tools, systemPrompt, maxTokens);
        break;
      case 'claude':
        response = await callClaude(apiKey, messages, tools, systemPrompt, maxTokens);
        break;
      case 'deepseek':
        response = await callDeepSeek(apiKey, messages, tools, systemPrompt, maxTokens);
        break;
      default:
        return res.status(400).json({ error: `Unknown provider: ${selectedProvider}` });
    }

    res.json(response);

  } catch (error) {
    console.error('Chat error:', error.message);
    res.status(500).json({ error: error.message });
  }
});

/**
 * Call OpenAI-compatible API (OpenAI, MiMo, etc.)
 */
async function callOpenAI(apiKey, messages, tools, systemPrompt, maxTokens) {
  // Use MiMo config if baseUrl is set, otherwise use OpenAI
  const baseUrl = MIMO_CONFIG.baseUrl || 'https://api.openai.com/v1';
  const model = MIMO_CONFIG.baseUrl ? MIMO_CONFIG.model : 'gpt-4o';

  const body = {
    model: model,
    max_tokens: maxTokens,
    temperature: 0.3,
    messages: []
  };

  // Add system prompt
  if (systemPrompt) {
    body.messages.push({ role: 'system', content: systemPrompt });
  }

  // Add conversation messages
  body.messages.push(...messages);

  // Add tools if provided
  if (tools && tools.length > 0) {
    body.tools = tools.map(tool => ({
      type: 'function',
      function: {
        name: tool.name,
        description: tool.description,
        parameters: tool.parameters
      }
    }));
    body.tool_choice = 'auto';
  }

  console.log(`Calling ${model} at ${baseUrl}/chat/completions`);

  const response = await fetch(`${baseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${apiKey}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`API error: ${response.status} - ${error}`);
  }

  const data = await response.json();
  return parseOpenAIResponse(data);
}

/**
 * Call Claude API
 */
async function callClaude(apiKey, messages, tools, systemPrompt, maxTokens) {
  const body = {
    model: 'claude-sonnet-4-20250514',
    max_tokens: maxTokens,
    messages: []
  };

  // Add system prompt
  if (systemPrompt) {
    body.system = systemPrompt;
  }

  // Convert messages to Claude format
  for (const msg of messages) {
    if (msg.role === 'tool') {
      // Tool result message
      body.messages.push({
        role: 'user',
        content: [{
          type: 'tool_result',
          tool_use_id: msg.toolCallId,
          content: msg.content
        }]
      });
    } else if (msg.role === 'assistant' && msg.toolCalls) {
      // Assistant message with tool calls
      const content = [];
      if (msg.content) {
        content.push({ type: 'text', text: msg.content });
      }
      for (const call of msg.toolCalls) {
        content.push({
          type: 'tool_use',
          id: call.id,
          name: call.name,
          input: call.arguments
        });
      }
      body.messages.push({ role: 'assistant', content });
    } else {
      body.messages.push({ role: msg.role, content: msg.content });
    }
  }

  // Add tools if provided
  if (tools && tools.length > 0) {
    body.tools = tools.map(tool => ({
      name: tool.name,
      description: tool.description,
      input_schema: tool.parameters
    }));
  }

  const response = await fetch('https://api.anthropic.com/v1/messages', {
    method: 'POST',
    headers: {
      'x-api-key': apiKey,
      'anthropic-version': '2023-06-01',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Claude API error: ${response.status} - ${error}`);
  }

  const data = await response.json();
  return parseClaudeResponse(data);
}

/**
 * Call DeepSeek API (OpenAI-compatible)
 */
async function callDeepSeek(apiKey, messages, tools, systemPrompt, maxTokens) {
  const body = {
    model: 'deepseek-chat',
    max_tokens: maxTokens,
    temperature: 0.3,
    messages: []
  };

  if (systemPrompt) {
    body.messages.push({ role: 'system', content: systemPrompt });
  }

  body.messages.push(...messages);

  if (tools && tools.length > 0) {
    body.tools = tools.map(tool => ({
      type: 'function',
      function: {
        name: tool.name,
        description: tool.description,
        parameters: tool.parameters
      }
    }));
    body.tool_choice = 'auto';
  }

  const response = await fetch('https://api.deepseek.com/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${apiKey}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`DeepSeek API error: ${response.status} - ${error}`);
  }

  const data = await response.json();
  return parseOpenAIResponse(data); // Same format as OpenAI
}

/**
 * Parse OpenAI response to unified format
 */
function parseOpenAIResponse(data) {
  const choice = data.choices?.[0];
  if (!choice) {
    return { text: 'No response', toolCalls: [] };
  }

  const message = choice.message;
  const toolCalls = [];

  if (message.tool_calls) {
    for (const call of message.tool_calls) {
      toolCalls.push({
        id: call.id,
        name: call.function.name,
        arguments: JSON.parse(call.function.arguments || '{}')
      });
    }
  }

  return {
    text: message.content || '',
    toolCalls,
    usage: data.usage ? {
      inputTokens: data.usage.prompt_tokens,
      outputTokens: data.usage.completion_tokens,
      totalTokens: data.usage.total_tokens
    } : null
  };
}

/**
 * Parse Claude response to unified format
 */
function parseClaudeResponse(data) {
  let text = '';
  const toolCalls = [];

  for (const block of data.content || []) {
    if (block.type === 'text') {
      text += block.text;
    } else if (block.type === 'tool_use') {
      toolCalls.push({
        id: block.id,
        name: block.name,
        arguments: block.input
      });
    }
  }

  return {
    text,
    toolCalls,
    usage: data.usage ? {
      inputTokens: data.usage.input_tokens,
      outputTokens: data.usage.output_tokens,
      totalTokens: data.usage.input_tokens + data.usage.output_tokens
    } : null
  };
}

// Start server
app.listen(PORT, () => {
  console.log(`🚀 Beresin Server running on http://localhost:${PORT}`);
  console.log(`📋 Health check: http://localhost:${PORT}/health`);
  console.log(`🤖 Chat endpoint: POST http://localhost:${PORT}/api/chat`);

  // Show MiMo config
  if (MIMO_CONFIG.baseUrl) {
    console.log(`🤖 MiMo server: ${MIMO_CONFIG.baseUrl}`);
    console.log(`📦 MiMo model: ${MIMO_CONFIG.model}`);
  }

  // Show configured providers
  const configured = [];
  if (API_KEYS.openai) configured.push(MIMO_CONFIG.baseUrl ? 'MiMo' : 'OpenAI');
  if (API_KEYS.claude) configured.push('Claude');
  if (API_KEYS.deepseek) configured.push('DeepSeek');

  if (configured.length > 0) {
    console.log(`✅ Configured providers: ${configured.join(', ')}`);
  } else {
    console.log(`⚠️  No API keys configured. Add to .env file.`);
  }
});
