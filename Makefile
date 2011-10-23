all:
	cd jni/serval-dna && sh ./nacl-jni-prep
	pwd 
	ndk-build
	ant debug
