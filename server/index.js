require('dotenv').config();
const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

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

// MiMo configuration (OpenAI format)
const MIMO_CONFIG = {
  baseUrl: process.env.MIMO_BASE_URL || '',
  model: process.env.MIMO_MODEL || 'MiMo-2.5-Pro',
};

// Default provider
const DEFAULT_PROVIDER = process.env.DEFAULT_PROVIDER || 'openai';
const FREE_DAILY_QUOTA = Number(process.env.FREE_DAILY_QUOTA || 20);
const PREMIUM_DAILY_QUOTA = Number(process.env.PREMIUM_DAILY_QUOTA || 1000);
const RATE_LIMIT_WINDOW_MS = Number(process.env.RATE_LIMIT_WINDOW_MS || 60_000);
const RATE_LIMIT_MAX = Number(process.env.RATE_LIMIT_MAX || 30);
const DATA_DIR = process.env.BERESIN_DATA_DIR || path.join(__dirname, '.data');
const QUOTA_FILE = path.join(DATA_DIR, 'quota.json');
const PREMIUM_TOKENS = new Set(
  (process.env.PREMIUM_TOKENS || process.env.DEV_PREMIUM_TOKENS || '')
    .split(',')
    .map(token => token.trim())
    .filter(Boolean)
);

const quotaStore = new Map();
const rateStore = new Map();

function loadQuotaStore() {
  try {
    if (!fs.existsSync(QUOTA_FILE)) return;

    const parsed = JSON.parse(fs.readFileSync(QUOTA_FILE, 'utf8'));
    const records = parsed.records || {};
    for (const [installId, record] of Object.entries(records)) {
      quotaStore.set(installId, {
        day: record.day,
        used: Number(record.used || 0),
        total: Number(record.total || FREE_DAILY_QUOTA),
        isPremium: Boolean(record.isPremium),
        chargedTurns: new Set(Array.isArray(record.chargedTurns) ? record.chargedTurns : [])
      });
    }
  } catch (error) {
    console.warn('Could not load quota store:', error.message);
  }
}

function persistQuotaStore() {
  try {
    fs.mkdirSync(DATA_DIR, { recursive: true });
    const records = {};
    for (const [installId, record] of quotaStore.entries()) {
      records[installId] = {
        day: record.day,
        used: record.used,
        total: record.total,
        isPremium: record.isPremium,
        chargedTurns: Array.from(record.chargedTurns).slice(-500)
      };
    }

    fs.writeFileSync(QUOTA_FILE, JSON.stringify({ version: 1, records }, null, 2));
  } catch (error) {
    console.warn('Could not persist quota store:', error.message);
  }
}

function getTodayKey() {
  return new Date().toISOString().slice(0, 10);
}

function getResetAt() {
  const reset = new Date();
  reset.setUTCHours(24, 0, 0, 0);
  return reset.toISOString();
}

function clientIp(req) {
  return req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.socket.remoteAddress || 'unknown';
}

function rateLimit(req, res, next) {
  const key = clientIp(req);
  const now = Date.now();
  const bucket = rateStore.get(key) || { count: 0, resetAt: now + RATE_LIMIT_WINDOW_MS };

  if (now > bucket.resetAt) {
    bucket.count = 0;
    bucket.resetAt = now + RATE_LIMIT_WINDOW_MS;
  }

  bucket.count += 1;
  rateStore.set(key, bucket);

  if (bucket.count > RATE_LIMIT_MAX) {
    return res.status(429).json({
      error: 'rate_limited',
      message: 'Too many requests. Try again shortly.',
      retryAfterMs: Math.max(0, bucket.resetAt - now)
    });
  }

  next();
}

function requireInstall(req, res, next) {
  const installId = String(req.headers['x-beresin-install-id'] || '').trim();
  if (!/^[a-f0-9-]{16,64}$/i.test(installId)) {
    return res.status(401).json({
      error: 'missing_install_id',
      message: 'Beresin install id is required'
    });
  }

  req.installId = installId;
  req.turnId = String(req.headers['x-beresin-turn-id'] || '').trim() || `turn-${Date.now()}`;
  req.isPremium = isPremiumRequest(req);
  next();
}

function isPremiumRequest(req) {
  const token = String(req.headers['x-beresin-premium-token'] || '').trim();
  if (!token) return false;
  if (PREMIUM_TOKENS.has(token)) return true;

  // Production hook: verify Google Play purchase tokens server-side here.
  // Do not trust the app to grant premium locally.
  return false;
}

function getQuotaRecord(installId, isPremium) {
  const today = getTodayKey();
  const existing = quotaStore.get(installId);
  const total = isPremium ? PREMIUM_DAILY_QUOTA : FREE_DAILY_QUOTA;

  if (!existing || existing.day !== today || existing.isPremium !== isPremium) {
    const created = {
      day: today,
      used: 0,
      total,
      isPremium,
      chargedTurns: new Set()
    };
    quotaStore.set(installId, created);
    persistQuotaStore();
    return created;
  }

  if (existing.total !== total || existing.isPremium !== isPremium) {
    existing.total = total;
    existing.isPremium = isPremium;
    persistQuotaStore();
  }
  return existing;
}

function consumeQuota(req, res, next) {
  const record = getQuotaRecord(req.installId, req.isPremium);

  if (!record.chargedTurns.has(req.turnId)) {
    if (record.used >= record.total) {
      return res.status(402).json({
        error: 'quota_exceeded',
        message: 'Daily quota exceeded',
        quota: buildQuota(record)
      });
    }

    record.used += 1;
    record.chargedTurns.add(req.turnId);
    persistQuotaStore();
  }

  req.quota = record;
  next();
}

function buildQuota(record) {
  return {
    remaining: Math.max(0, record.total - record.used),
    total: record.total,
    isPremium: record.isPremium,
    resetAt: getResetAt()
  };
}

loadQuotaStore();

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
      name: MIMO_CONFIG.baseUrl ? 'Xiaomi MiMo 2.5 Pro' : 'OpenAI',
      model: MIMO_CONFIG.model || 'gpt-4o'
    });
  }
  if (API_KEYS.claude) {
    providers.push({
      id: 'claude',
      name: 'Claude',
      model: 'claude-sonnet-4-20250514'
    });
  }
  if (API_KEYS.deepseek) {
    providers.push({
      id: 'deepseek',
      name: 'DeepSeek',
      model: 'deepseek-chat'
    });
  }

  res.json({
    providers,
    default: DEFAULT_PROVIDER
  });
});

/**
 * OpenAI-compatible endpoint (for direct app calls)
 */
app.post('/v1/chat/completions', rateLimit, requireInstall, consumeQuota, async (req, res) => {
  try {
    const { messages, tools, max_tokens = 4096 } = req.body;

    const apiKey = API_KEYS.openai;
    if (!apiKey) {
      return res.status(400).json({ error: 'MiMo API key not configured' });
    }

    const baseUrl = MIMO_CONFIG.baseUrl || 'https://api.openai.com/v1';
    const model = MIMO_CONFIG.model || 'gpt-4o';

    const body = {
      model,
      max_tokens,
      temperature: 0.3,
      messages
    };

    if (tools && tools.length > 0) {
      body.tools = tools;
      body.tool_choice = 'auto';
    }

    console.log(`[v1] Calling ${model} at ${baseUrl}/chat/completions`);

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
    res.setHeader('X-Beresin-Quota-Remaining', buildQuota(req.quota).remaining);
    res.json(data); // Pass through directly (already OpenAI format)

  } catch (error) {
    console.error('[v1] Chat error:', error.message);
    res.status(500).json({ error: error.message });
  }
});

/**
 * Chat completion endpoint (proxies to AI providers)
 */
app.post('/api/chat', rateLimit, requireInstall, consumeQuota, async (req, res) => {
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

    res.json({
      ...response,
      quota: buildQuota(req.quota)
    });

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
 * Supports MiMo reasoning_content field
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

  // MiMo has reasoning_content field
  const text = message.content || message.reasoning_content || '';

  return {
    text,
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
    console.log(`\n🤖 Xiaomi MiMo 2.5 Pro Configuration:`);
    console.log(`   Base URL: ${MIMO_CONFIG.baseUrl}`);
    console.log(`   Model: ${MIMO_CONFIG.model}`);
    console.log(`   Format: OpenAI Compatible`);
  }

  // Show configured providers
  const configured = [];
  if (API_KEYS.openai) configured.push(MIMO_CONFIG.baseUrl ? 'MiMo' : 'OpenAI');
  if (API_KEYS.claude) configured.push('Claude');
  if (API_KEYS.deepseek) configured.push('DeepSeek');

  if (configured.length > 0) {
    console.log(`\n✅ Configured providers: ${configured.join(', ')}`);
  } else {
    console.log(`\n⚠️  No API keys configured. Add to .env file.`);
  }
});
