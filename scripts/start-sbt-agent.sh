#!/bin/bash

SESSION_NAME="sbt-agent"
LOG_FILE="sbt-output.log"

# Start sbt in a tmux session if it isn't already running
if ! tmux has-session -t "$SESSION_NAME" 2>/dev/null; then
  echo "Starting sbt in tmux session: $SESSION_NAME"
  tmux new-session -d -s "$SESSION_NAME" 'sbt'
  sleep 10  # Allow time for sbt shell to initialize
  tmux pipe-pane -t "$SESSION_NAME" -o "cat >> $LOG_FILE"
else
  echo "Session $SESSION_NAME already running."
fi
