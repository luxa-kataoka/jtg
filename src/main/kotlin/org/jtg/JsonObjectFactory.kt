package org.jtg

import com.google.gson.*
import com.google.gson.reflect.*
import com.google.gson.internal.LinkedTreeMap

object JsonObjectFactory {
	
	fun fromJsonObject(jsonString : String) : List<LinkedTreeMap<String, Any?>> {
		val gson = createGsonInstance()
		val returnType = object : TypeToken<LinkedTreeMap<String, Any?>>(){}.getType()
		return gson.fromJson(jsonString, returnType)
	}
	
	
	fun fromJsonArray(jsonString : String) : List<LinkedTreeMap<String, Any?>>{
		val gson = createGsonInstance()
		val returnType = object :  TypeToken<List<LinkedTreeMap<String, Any?>>>(){}.getType()
		return gson.fromJson(jsonString, returnType)
	}
	
	private fun createGsonInstance() : Gson {
		 return GsonBuilder().serializeNulls().create()!!
	}
}