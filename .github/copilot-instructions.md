# 🧠 Copilot Instructions: Efficient sbt Workflow for Agentic Use

## 🏗️ Goal

To minimize startup time when running `sbt` commands (e.g., `compile`, `test`, `run`) in this Scala project, GitHub Copilot or other AI agents should interact with a **persistent background sbt shell** running in a `tmux` session.

---

## ⚙️ Setup: Start sbt in a Persistent Background Session

Use the `start-sbt-agent.sh` script included in this repository:

```bash
./scripts/start-sbt-agent.sh
```

This will:
- Launch `sbt` inside a `tmux` session (named `sbt-agent`) if not already running
- Wait for initialization
- Stream all output to `sbt-output.log`

---

## ▶️ Executing Commands

To execute sbt commands like `test`, `compile`, or `run`, inject the command into the running tmux session:

```bash
tmux send-keys -t sbt-agent "test" C-m
```

Replace `"test"` with any other valid sbt command.

---

## 📤 Reading Output (Continuous Streaming)

All output from the sbt shell is streamed to `sbt-output.log` automatically by the `start-sbt-agent.sh` script.

Agents should monitor and parse this log file for command output and status.

---

## 🧪 Example Workflow: Agent Loop

```bash
# Start session if not running
./scripts/start-sbt-agent.sh

# Send sbt command
tmux send-keys -t sbt-agent "test" C-m

# Read from sbt-output.log as needed
```

---

## 🧹 Teardown

When the agent is finished, shut down the sbt session with:

```bash
./scripts/stop-sbt-agent.sh
```

This script will terminate the `tmux` session cleanly.

---

## 📎 Notes for Agent Integration

- ✅ Prefer **persistent sessions** over invoking `sbt` per command (slow).
- ✅ Use `tmux send-keys` for command dispatching.
- ✅ Parse output via `sbt-output.log`.
- 🚫 Do not run `sbt` from scratch every time — JVM startup will slow down execution dramatically.

---

## 🔁 Recap

| Task              | Command                                     |
|-------------------|---------------------------------------------|
| Start sbt session | `./scripts/start-sbt-agent.sh`              |
| Send command      | `tmux send-keys -t sbt-agent "compile" C-m` |
| View output       | Read `sbt-output.log`                       |
| Stop session      | `./scripts/stop-sbt-agent.sh`               |
