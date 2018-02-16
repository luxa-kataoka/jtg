package org.jtg

import java.io.*

fun main(args : Array<String>){
	println("test")

	val filepath = if( args.size > 0) args.get(0) else "test.json"
	val inputFile = File(filepath)
	val jsonString = inputFile.readLines().joinToString()
}
