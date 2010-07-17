The example web will access channel images using this format: 
/picon/serviceid.profilename.ext (where ext is png by default, and serviceid is decimal not hex).

For every http get that follows this format, the httpd will try an exact match. 
I.e. it will look for a files called for example: 1135.myprofile.png.
If no such file exists, it will look up the name of service 1135 (0x046f) in the services file for myprofile.
If it finds a name, lets say "SAT CHANNEL X", it will look for the file "sat_channel_x.png" instead.

If no file can be found using either method, the file unknown.png will be returned.