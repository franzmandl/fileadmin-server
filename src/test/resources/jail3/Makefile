all:: all01.txt noPermDir01 noPermFile01

all01.txt: checkbox01.txt markdownImage01.txt markdownUrl01.txt rawUrl01.txt
	cat $^ > $@

clean::
	rm -rf noPermDir01 noPermFile01

noPermDir01::
	mkdir -p noPermDir01
	chmod 000 noPermDir01

noPermFile01::
	touch noPermFile01
	chmod 000 noPermFile01
