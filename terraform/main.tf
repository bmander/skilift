provider "google" {
  project = var.project != "" ? var.project : var.env_project
  region  = var.region
}

resource "google_compute_instance" "default" {
  name         = "vm-instance"
  machine_type = "e2-medium"
  zone         = var.zone

  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-10"
    }
  }

  network_interface {
    network = "default"
    access_config {
    }
  }
}

variable "project" {
  description = "The project ID to deploy to"
  default     = ""
}

variable "env_project" {
  description = "The project ID from the environment variable"
  default     = "${env.GCP_PROJECT_ID}"
}

variable "region" {
  description = "The region to deploy to"
  default     = "us-central1"
}

variable "zone" {
  description = "The zone to deploy to"
  default     = "us-central1-a"
}
