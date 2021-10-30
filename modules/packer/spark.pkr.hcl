variables {
  version     = "0.1.0",
  region      = "{{ env `AWS_REGION`}}",
  environment = "{{ env `ENVIRONMENT`}}"
}

source "amazon-ebs" "ami_source_definition" {
  ami_name             = "spark"
  instance_type        = "t3.micro"
  region               = "{{user `region`}}"
  source_ami           = "ami-02ace471"
  ssh_username         = "ec2-user"
  ssh_pty              = false
  ssh_private_key_file = true
  tags                 = {
    Name    = "spark"
    Version = "{{user `version`}}"
  }
}

build {
  sources = [
    "source.amazon-ebs.ami_source_definition"
  ]
  provisioner "ansible" {
    playbook_dir : "ansible"
    playbook_file = "spark.yml"
    extra_arguments : ["--extra-vars \"Environment={{user `environment`}}\""]
  }
}

