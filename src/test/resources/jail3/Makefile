all:: all01.txt doesNotExist01 noPermDirectory01 noPermFile01 virtualLink01

all01.txt: checkbox01.txt markdownImage01.txt markdownUrl01.txt rawUrl01.txt
	cat $^ > $@

clean::
	rm -rf doesNotExist01 noPermDirectory01 noPermFile01 virtualLink01

doesNotExist01::
	ln -s doesNotExist doesNotExist01

noPermDirectory01::
	mkdir -p noPermDirectory01
	chmod 000 noPermDirectory01

noPermFile01::
	touch noPermFile01
	chmod 000 noPermFile01

virtualLink01::
	ln -s filterJail4/example1/tags/person virtualLink01
