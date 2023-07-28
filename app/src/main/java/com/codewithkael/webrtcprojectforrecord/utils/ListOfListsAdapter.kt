package com.codewithkael.webrtcprojectforrecord.utils

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter

class ListOfListsAdapter(private val listOfLists: ArrayList<Any>) :BaseAdapter() {
    override fun getCount(): Int {
        return listOfLists.toList().size
    }

    override fun getItem(position: Int): Any {
        return listOfLists[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        TODO("Not yet implemented")

    }

}