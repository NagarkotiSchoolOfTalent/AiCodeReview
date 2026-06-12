const OpenAI = require("openai");

const ANTHROPIC_API_KEY = process.env.ANTHROPIC_API_KEY;
const GITHUB_TOKEN = process.env.GITHUB_TOKEN;
const PR_NUMBER = process.env.PR_NUMBER;
const REPO = process.env.REPO;
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

  // FIX: Read as text first — res.json() throws "Unexpected end of JSON input"
  // on empty body (DELETE returns 204 No Content, first PR has no comments yet)
  const text = await res.text();
  if (!text || text.trim() === "") return null;

  try {
    return JSON.parse(text);
  } catch (e) {
    throw new Error(`GitHub API ${path} → invalid JSON: "${text}"`);
  }
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

  // FIX: Guard against null/empty — happens on first PR with no comments yet
  if (!comments || !Array.isArray(comments)) {
    console.log("No existing comments found — nothing to delete.");
    return;
  }

  const botComments = comments.filter(
    (c) =>
      c.user.type === "Bot" &&
      c.body.includes("<!-- claude-ai-review -->")
  );

  if (botComments.length === 0) {
    console.log("No previous review comments to delete.");
    return;
  }

  for (const c of botComments) {
    await githubRequest(
      `/repos/${OWNER}/${REPO_NAME}/issues/comments/${c.id}`,
      { method: "DELETE" }
    );
    console.log(`🗑️  Deleted old review comment id=${c.id}`);
  }
}

// ─── Diff truncation ───────────────────────────────────────────────────────

const MAX_DIFF_CHARS = 60_000;
const SKIP_EXTENSIONS = [
  ".lock", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
  ".woff", ".woff2", ".ttf", ".eot", ".mp4", ".mp3", ".pdf", ".zip",
];

function filterDiff(raw) {
  const files = raw.split(/^diff --git /m).filter(Boolean);
  const filtered = files.filter((chunk) => {
    const firstLine = chunk.split("\n")[0];
    return !SKIP_EXTENSIONS.some((ext) => firstLine.endsWith(ext));
  });
  const joined = filtered.map((c) => `diff --git ${c}`).join("");
  return joined.length > MAX_DIFF_CHARS
    ? joined.slice(0, MAX_DIFF_CHARS) + "\n\n[... diff truncated for length ...]"
    : joined;
}

// ─── Critical issue detection ──────────────────────────────────────────────
function hasCriticalIssues(reviewText) {
  console.log("🔍 Scanning review text for critical issues...");

  // Log the full review text so we can debug exactly what OpenAI returned
  console.log("--- FULL REVIEW TEXT START ---");
  console.log(reviewText);
  console.log("--- FULL REVIEW TEXT END ---");

  // Strategy: find the "Critical Issues" section regardless of how OpenAI formats it.
  // OpenAI may return any of these formats:
  //   ## 🔴 Critical Issues      (ideal)
  //   ## Critical Issues         (no emoji in heading)
  //   🔴 Critical Issues         (emoji as bullet, no ##)
  //   **Critical Issues**        (bold, no heading)
  // So we search for the PHRASE "critical issue" anywhere, then check if
  // there is meaningful content after it.

  // Find where "critical issue(s)" appears in the text (case insensitive)
  const criticalIndex = reviewText.search(/critical\s+issues?/i);

  if (criticalIndex === -1) {
    console.log("   'Critical Issues' phrase not found anywhere in review.");
    return false;
  }

  console.log(`   Found 'Critical Issues' at index ${criticalIndex}`);

  // Get everything after the "critical issues" heading line
  const afterCritical = reviewText.slice(criticalIndex).split('\n').slice(1).join('\n').trim();
  console.log(`   Content after heading (first 200 chars): ${afterCritical.substring(0, 200)}`);

  // Empty section or explicitly says none — no real issues
  if (
    afterCritical.length === 0 ||
    /^(none|no critical issues?|n\/a|\.?)$/i.test(afterCritical.split('\n')[0].trim())
  ) {
    console.log("   Critical section is empty or says none — no block.");
    return false;
  }

  // Check the content after heading is not just the next section header
  // (i.e. there is actual content between Critical Issues and the next ##)
  const nextSectionMatch = afterCritical.match(/^([\s\S]*?)(?=\n#{1,3}\s|\n🟡|\n✅|$)/);
  const sectionBody = nextSectionMatch ? nextSectionMatch[1].trim() : afterCritical.trim();

  console.log(`   Section body length: ${sectionBody.length} chars`);

  if (sectionBody.length < 10) {
    console.log("   Critical section body too short — treating as empty.");
    return false;
  }

  console.log("   ✅ CRITICAL ISSUES DETECTED — exiting with code 1 — merge BLOCKED.");
  return true;
}

// ─── OpenAI review ────────────────────────────────────────────────────────

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

IMPORTANT: You MUST use EXACTLY this heading for critical issues (copy exactly):
## 🔴 Critical Issues

Format your response with these exact section headings:
## Summary
## 🔴 Critical Issues
## 🟡 Warnings
## 🔵 Suggestions
## ✅ What's done well

If a section has no items, omit it entirely.`;

async function reviewWithOpenAI(diff, prTitle, prAuthor) {
  if (!ANTHROPIC_API_KEY) {
    throw new Error("ANTHROPIC_API_KEY environment variable is not set");
  }

  const client = new OpenAI({
    apiKey: ANTHROPIC_API_KEY,
    timeout: 60000,
  });

  const userMessage = `PR: "${prTitle}" by @${prAuthor}

\`\`\`diff
${diff}
\`\`\`

Please review this pull request diff and provide detailed feedback.`;

  console.log("📡 Sending request to OpenAI API...");
  console.log(`   Model: gpt-4o-mini`);
  console.log(`   Diff length: ${diff.length} characters`);

  try {
    const response = await client.chat.completions.create({
      model: "gpt-4o-mini",
      max_tokens: 4096,
      messages: [
        { role: "system", content: SYSTEM_PROMPT },
        { role: "user", content: userMessage }
      ],
    });

    console.log("✓ Received response from OpenAI");

    if (!response) throw new Error("Response object is null or undefined");
    if (!Array.isArray(response.choices) || response.choices.length === 0) {
      throw new Error(`No choices in response: ${JSON.stringify(response)}`);
    }

    const choice = response.choices[0];
    if (!choice.message) throw new Error(`No message in first choice: ${JSON.stringify(choice)}`);

    const content = choice.message.content;
    if (!content || typeof content !== "string") {
      throw new Error(`No text content in message: ${JSON.stringify(choice.message)}`);
    }

    console.log(`✓ Got review content (${content.length} characters)`);
    return content;

  } catch (error) {
    console.error("🔴 OpenAI API Error");
    console.error(`   Error type: ${error.constructor.name}`);
    console.error(`   Message: ${error.message}`);
    if (error.status) console.error(`   Status code: ${error.status}`);
    throw error;
  }
}

// ─── Main ───────────────────────────────────────────────────────────

async function main() {
  console.log(`🤖 Starting AI code review for PR #${PR_NUMBER} in ${REPO}`);

  console.log("📄 Fetching diff...");
  const rawDiff = await getDiff();
  const diff = filterDiff(rawDiff);
  console.log(`   Diff size: ${diff.length} characters`);

  if (diff.trim().length < 20) {
    console.log("No meaningful diff found — skipping review.");
    return;
  }

  console.log("🧠 Sending to OpenAI for review...");
  const reviewText = await reviewWithOpenAI(diff, PR_TITLE, PR_AUTHOR);

  // Detect critical issues — drives exit code and merge blocking
  const criticalFound = hasCriticalIssues(reviewText);

  const mergeStatus = criticalFound
    ? "🚫 **Merge is BLOCKED** — fix all 🔴 Critical Issues above before this PR can be merged."
    : "✅ **Merge is ALLOWED** — no critical issues found. Warnings and suggestions are optional.";

  const commentBody = `<!-- claude-ai-review -->
## 🤖 AI Code Review

${reviewText}

---
### Merge Status
${mergeStatus}

<sub>Reviewed by OpenAI · ${new Date().toUTCString()}</sub>`;

  console.log("🗑️  Removing previous review comments...");
  await deleteOldReviews();

  console.log("💬 Posting review comment...");
  await postComment(commentBody);
  console.log(`✅ Review posted to PR #${PR_NUMBER}`);

  // Exit code controls GitHub status check:
  //   exit(1) → workflow FAILS  → merge BLOCKED  (branch protection enforces this)
  //   exit(0) → workflow PASSES → merge ALLOWED
  if (criticalFound) {
    console.log("❌ Exiting with code 1 — CRITICAL ISSUES FOUND — merge is BLOCKED.");
    process.exit(1);
  } else {
    console.log("✅ Exiting with code 0 — no critical issues — merge is allowed.");
    process.exit(0);
  }
}

main().catch((err) => {
  console.error("❌ Review failed:", err.message);
  console.error("Stack trace:", err.stack);
  process.exit(1);
});
