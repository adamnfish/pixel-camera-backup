#!/bin/bash

SESSION_NAME="sbt-agent"

# Stop the tmux session if it exists
if tmux has-session -t "$SESSION_NAME" 2>/dev/null; then
  echo "Stopping tmux session: $SESSION_NAME"
  tmux kill-session -t "$SESSION_NAME"
else
  echo "No session named $SESSION_NAME is running."
fi
