import expect from 'expect.js';
import activate from '../src/js/client/VertxProtoStub';

describe('GlobalRegistryConnector', function() {
	let protoURL = 'hyperty-runtime://localhost/123/graph-connector';

	let config = {
    url: 'https://msg-node.localhost:9091/eventbus',
    vertxbus_ping_interval: 10000,
    host: 'sharing-cities-dsm',
    streams: [
    ],
    publicWallets : {
        identity: {
          userProfile: {
            guid: 'user-guid://public-wallets',
            userURL: 'user://public-wallets'
          }
        }
      },
    timeoutValue: 500
	};

	it('createwallet', function(done) {


    let createMessage = {
      type: 'forward', to: 'hyperty://sharing-cities-dsm/wallet-manager', from: _this.hypertyURL,
      identity: { userProfile: { guid: 'user-guid://4b5599a60fddfac5f51412ec69c9b1f3f8e60176f5ff1be8a66f100fec51dade'} },
      body: {
        type: 'create',
        from: _this.hypertyURL,
        resource: 'wallet'
      }
    };


		this.timeout(20000);

		let send;

		let bus = {
			postMessage: (createMessage) => {
				console.log('Test reply->>>>>>>>>: ', JSON.stringify(msg));


					done();
				}
			},

			addListener: (url, callback) => {
				console.log('addListener: ', url);
				send = callback;
			}
		};

		let proto = activate(protoURL, bus, config).activate;

		send({
			id: 64, type: 'CREATE', from: 'hyperty-runtime://localhost/123/graph-connector', to: 'global://registry/',
			body: { guid: 'puZE5qCSGqcjg5mViBM3CdQHKIcHpRoyF3OGLTaYzGs', jwt: 'eyJhbGciOiJFUzI1NiJ9.eyJkYXRhIjoiZXlKbmRXbGtJam9pY0hWYVJUVnhRMU5IY1dOcVp6VnRWbWxDVFRORFpGRklTMGxqU0hCU2IzbEdNMDlIVEZSaFdYcEhjeUlzSW5CMVlteHBZMHRsZVNJNklpMHRMUzB0UWtWSFNVNGdVRlZDVEVsRElFdEZXUzB0TFMwdFRVWlpkMFZCV1VoTGIxcEplbW93UTBGUldVWkxORVZGUVVGdlJGRm5RVVZUTFVoek5ubG5RVEZpWm05c1kwMVBSR2RRT1ZkR1dqSlVTVFp3Tm1VNUxVMUxabEJuV1d0Mk1FVjNhSEF5T1ROVmRGOUdlbTVUWlV0dlRqQTRNemd3YkRCU1NGbDRabTlxTmxacVREaE9XRGxOVGtaa1p5MHRMUzB0UlU1RUlGQlZRa3hKUXlCTFJWa3RMUzB0TFNJc0lteGhjM1JWY0dSaGRHVWlPaUl5TURFMUxUQTVMVEkwVkRBNE9qSTBPakkzS3pBd09qQXdJaXdpWVdOMGFYWmxJam94TENKMWMyVnlTVVJ6SWpwYkluSmxWRWhKVGtzNkx5OXpaV0poYzNScFlXNHVaMjlsYm1SdlpYSXVibVYwTHlJc0luSmxWRWhKVGtzNkx5OW1ZV05sWW05dmF5NWpiMjB2Wm14MVptWjVNVEl6SWwwc0luSmxkbTlyWldRaU9qQXNJblJwYldWdmRYUWlPaUl5TURJMkxUQTVMVEkwVkRBNE9qSTBPakkzS3pBd09qQXdJaXdpYzJGc2RDSTZJbE53U0hWWWQwVkhkM0pPWTBWalJtOU9VemhMZGpjNVVIbEhSbXg0YVRGMkluMCJ9.MEYCIQCxuGtdM8HqcM0G-PxT7mGKUM6-cMaCiLF4PtT2aGQXRwIhAM6z5t5f1Mpgjw74mXL-NJQ8NcaX_SYTVyGmR8XIVCcF' }
		});
	});


});
