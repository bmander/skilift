provider "google" {
  project = var.GCP_PROJECT_ID
  region  = var.region
}

resource "google_compute_instance" "default" {
  name         = "vm-instance"
  machine_type = "e2-highmem-2"
  zone         = var.zone

  boot_disk {
    initialize_params {
      image = "projects/cos-cloud/global/images/cos-stable-117-18613-164-13"
      size  = 100
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
