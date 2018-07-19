require 'json'

task :build do
  sh 'mvn clean install -DskipTests=true'
end

task :publish do
  require 'faraday'

  connection = Faraday.new('http://localhost:8080') do |c|
    c.request :multipart
    c.request :url_encoded
    c.adapter :excon
    # c.basic_auth('', config.fetch(:password))
  end

  response = connection.get('/crumbIssuer/api/json')
  headers = if response.status == 404
    {}
  else
    crumb = JSON.parse(response.body, symbolize_names: true)
    headers = { crumb.fetch(:crumbRequestField) => crumb.fetch(:crumb) }
  end

  v = Dir.glob("#{Dir.pwd}/target/*.hpi").at(0)
  payload = {}
  payload[:hpi] = Faraday::UploadIO.new(v, 'application/binary')
  response = connection.post('/pluginManager/uploadPlugin', payload, headers)
  p response.status
  raise 'expected status 302 on upload' unless response.status == 302

  response = connection.post('/restart', headers)
  raise 'expected status 302 on restart' unless response.status == 302
end

task :publish => :build
task :default => :publish
