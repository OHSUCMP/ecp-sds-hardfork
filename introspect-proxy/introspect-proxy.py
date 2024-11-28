#!/usr/bin/env python3

from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import jwt


listen_port = int( sys.argv[1] )
response_filename = sys.argv[2]

class IntrospectHTTPRequestHandler(BaseHTTPRequestHandler):
  def do_POST( self ):
        auth_header = self.headers.get('Authorization')
        if auth_header and auth_header.startswith('Bearer '):
            try:
                token = auth_header.split(' ')[1]
                payload = jwt.decode(token, options={"verify_signature": False})
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(payload).encode())
            except:
                self.send_response(404)
                self.send_header('Content-type', 'text/plain')
                self.end_headers()
                self.wfile.write(b'Unauthorized')
        else:
            self.send_response(404)
            self.send_header('Content-type', 'text/plain')
            self.end_headers()
            self.wfile.write(b'Unauthorized')

with HTTPServer( ('', listen_port), IntrospectHTTPRequestHandler ) as server:
    server.serve_forever()
