# Minimal Dockerfile for the dev container
FROM mcr.microsoft.com/vscode/devcontainers/base:0-bullseye

# Install necessary packages
RUN apt-get update && apt-get install -y \
    openjdk-11-jdk \
    gradle \
    python3 \
    python3-pip \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt /tmp/
RUN pip3 install --no-cache-dir -r /tmp/requirements.txt && rm /tmp/requirements.txt

# Set the working directory
WORKDIR /workspace
