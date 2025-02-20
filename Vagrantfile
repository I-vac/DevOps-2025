require 'dotenv'
Dotenv.load

Vagrant.configure("2") do |config|
    config.vm.box = "digital_ocean"

    config.vm.provider :digital_ocean do |do_config|
      do_config.token = ENV['DIGITAL_OCEAN_TOKEN']
      do_config.image = "ubuntu-22-04-x64"
      do_config.region = "fra1"
      do_config.size = "s-1vcpu-1gb"
      do_config.ssh_key_name = "SSH Key"
    end

      # Fix NFS error by disabling shared folders
    config.vm.synced_folder ".", "/vagrant", disabled: true

    config.ssh.private_key_path = "~/.ssh/id_ed25519"

    config.vm.provision "shell", inline: <<-SHELL

      apt update && apt install -y docker.io docker-compose git
  
      usermod -aG docker $USER
  
      git clone https://github.com/I-vac/DevOps-2025 /opt/minitwit
  
      cd /opt/minitwit
  
      docker-compose up -d --build
    SHELL
  end
  