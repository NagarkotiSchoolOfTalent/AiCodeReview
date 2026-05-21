/**
 * Claude AI Code Review Script
 * Fetches PR diff, sends to Claude, posts review as GitHub PR comment.
 */

const Anthropic = require("openai");

const ANTHROPIC_API_KEY = process.env.ANTHROPIC_API_KEY;
const GITHUB_TOKEN = process.env.GITHUB_TOKEN;
const PR_NUMBER = process.env.PR_NUMBER;
const REPO = process.env.REPO; // e.g. "owner/repo"
const BASE_SHA = process.env.BASE_SHA;
const HEAD_SHA = process.env.HEAD_SHA;
const PR_TITLE = process.env.PR_TITLE || "";
const PR_AUTHOR = process.env.PR_AUTHOR || "";

const [OWNER, REPO_NAME] = REPO.split("/");

// ─── GitHub API helpers ────────────────────────────────────────────────────

async function githubRequest(path, options = {}) {
  const url = `https://api.github.com${path}`;
  const res = await fetch(url, {
    ...options,
    headers: {
      Authorization: `Bearer ${GITHUB_TOKEN}`,
      Accept: "application/vnd.github.v3+json",
      "Content-Type": "application/json",
      "X-GitHub-Api-Version": "2022-11-28",
      ...options.headers,
    },
  });

  if (!res.ok) {
    const body = await res.text();
    throw new Error(`GitHub API ${path} → ${res.status}: ${body}`);
  }

  return res.json();
}

async function getDiff() {
  const url = `https://api.github.com/repos/${OWNER}/${REPO_NAME}/compare/${BASE_SHA}...${HEAD_SHA}`;
  const res = await fetch(url, {
    headers: {
      Authorization: `Bearer ${GITHUB_TOKEN}`,
      Accept: "application/vnd.github.v3.diff",
      "X-GitHub-Api-Version": "2022-11-28",
    },
  });
  if (!res.ok) throw new Error(`Failed to fetch diff: ${res.status}`);
  return res.text();
}

async function postComment(body) {
  return githubRequest(
    `/repos/${OWNER}/${REPO_NAME}/issues/${PR_NUMBER}/comments`,
    { method: "POST", body: JSON.stringify({ body }) }
  );
}

async function deleteOldReviews() {
  const comments = await githubRequest(
    `/repos/${OWNER}/${REPO_NAME}/issues/${PR_NUMBER}/comments`
  );
  const botComments = comments.filter(
    (c) =>
      c.user.type === "Bot" &&
      c.body.includes("<!-- claude-ai-review -->")
  );
  for (const c of botComments) {
    await githubRequest(
      `/repos/${OWNER}/${REPO_NAME}/issues/comments/${c.id}`,
      { method: "DELETE" }
    );
  }
}

// ─── Diff truncation ───────────────────────────────────────────────────────

const MAX_DIFF_CHARS = 60_000;
const SKIP_EXTENSIONS = [
  ".lock",
  ".png",
  ".jpg",
  ".jpeg",
  ".gif",
  ".svg",
  ".ico",
  ".woff",
  ".woff2",
  ".ttf",
  ".eot",
  ".mp4",
  ".mp3",
  ".pdf",
  ".zip",
];

function filterDiff(raw) {
  const files = raw.split(/^diff --git /m).filter(Boolean);
  const filtered = files.filter((chunk) => {
    const firstLine = chunk.split("\n")[0];
    return !SKIP_EXTENSIONS.some((ext) => firstLine.endsWith(ext));
  });
  const joined = filtered.map((c) => `diff --git ${c}`).join("");
  return joined.length > MAX_DIFF_CHARS
    ? joined.slice(0, MAX_DIFF_CHARS) +
        "\n\n[... diff truncated for length ...]"
    : joined;
}

// ─── Claude review ─────────────────────────────────────────────────────────

const SYSTEM_PROMPT = `You are an expert senior software engineer performing a thorough code review.
Your goal is to help the developer improve their code by identifying real issues with clear, actionable explanations.

Review criteria (in priority order):
1. **Bugs & correctness** — logic errors, off-by-one, unhandled edge cases, race conditions
2. **Security** — injection, XSS, insecure auth, secrets in code, unsafe deserialization
3. **Performance** — unnecessary loops, missing indexes, N+1 queries, memory leaks
4. **Code quality** — readability, naming, duplication, SOLID violations, dead code
5. **Tests** — missing coverage for important paths, weak assertions

Rules for your response:
- Be specific: reference file names, line numbers, and function names when possible
- Explain WHY something is an issue, not just what it is
- Suggest concrete fixes with code snippets when helpful
- Group findings by severity: 🔴 Critical, 🟡 Warning, 🔵 Suggestion
- If the code is genuinely good, say so briefly and skip empty sections
- Keep your total response under 3000 words
- Use GitHub-flavoured Markdown

Format your response with these sections:
## Summary
(1–3 sentence overall assessment)

## 🔴 Critical Issues
(bugs, security holes — must fix before merge)

## 🟡 Warnings
(non-blocking but important)

## 🔵 Suggestions
(style, minor improvements)

## ✅ What's done well
(positive reinforcement — always include at least one thing)

If a section has no items, omit it entirely.`;

async function reviewWithClaude(diff, prTitle, prAuthor) {
  const client = new Anthropic({ apiKey: ANTHROPIC_API_KEY });

  const userMessage = `PR: "${prTitle}" by @${prAuthor}

\`\`\`diff
${diff}
\`\`\`

Please review this pull request diff and provide detailed feedback.`;

  const response = await client.messages.create({
    model: "gpt-4.1-mini",
    max_tokens: 4096,
    system: SYSTEM_PROMPT,
    messages: [{ role: "user", content: userMessage }],
  });

  return response.content[0].text;
}

// ─── Main ──────────────────────────────────────────────────────────────────

async function main() {
  console.log(`🤖 Starting Claude review for PR #${PR_NUMBER} in ${REPO}`);

  // Fetch and filter diff
  console.log("📄 Fetching diff...");
  const rawDiff = await getDiff();
  const diff = filterDiff(rawDiff);
  console.log(`   Diff size: ${diff.length} characters`);

  if (diff.trim().length < 20) {
    console.log("No meaningful diff found — skipping review.");
    return;
  }

  // Call Claude
  console.log("🧠 Sending to Claude for review...");
  const reviewText = await reviewWithClaude(diff, PR_TITLE, PR_AUTHOR);

  // Delete previous bot review comments (on re-push)
  console.log("🗑️  Removing previous review comments...");
  await deleteOldReviews();

  // Post new comment
  const commentBody = `<!-- claude-ai-review -->
## 🤖 Claude AI Code Review

${reviewText}

---
<sub>Reviewed by Claude · ${new Date().toUTCString()} · [What is this?](https://docs.anthropic.com/)</sub>`;

  console.log("💬 Posting review comment...");
  await postComment(commentBody);
  console.log(`✅ Review posted to PR #${PR_NUMBER}`);
}

main().catch((err) => {
  console.error("❌ Review failed:", err.message);
  process.exit(1);
});
