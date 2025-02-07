provider "google" {
  project = var.GCP_PROJECT_ID
  region  = var.region
}

resource "google_compute_instance" "default" {
  name         = "vm-instance"
  machine_type = "e2-medium"
  zone         = var.zone

  boot_disk {
    initialize_params {
      image = "debian-12-bookworm-v20250113"
    }
  }

  network_interface {
    network = "default"
    access_config {
    }
  }
}

variable "GCP_PROJECT_ID" {
  description = "The project ID to deploy to"
}

variable "region" {
  description = "The region to deploy to"
  default     = "us-central1"
}

variable "zone" {
  description = "The zone to deploy to"
  default     = "us-central1-a"
}
