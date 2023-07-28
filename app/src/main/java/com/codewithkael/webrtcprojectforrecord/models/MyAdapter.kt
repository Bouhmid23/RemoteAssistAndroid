package com.codewithkael.webrtcprojectforrecord.models

import android.app.Activity
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.codewithkael.webrtcprojectforrecord.R

class MyAdapter(private val context :Activity, arrayList: ArrayList<User>) :
    ArrayAdapter<User>(context, R.layout.user_list,arrayList){

    private class ViewHolder {
        lateinit var nameTextView: TextView
        lateinit var stateTextView: TextView }

    // green in RGB
    private val greenColor = Color.rgb(0, 180, 0)

    // red in RGB
    private val redColor = Color.rgb(255, 0, 0)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        var itemView = convertView
        val viewHolder : ViewHolder

        if(itemView == null){
            itemView = LayoutInflater.from(context).inflate(R.layout.user_list,parent,false)
            viewHolder = ViewHolder()
            viewHolder.nameTextView = itemView.findViewById(R.id.personName)
            viewHolder.stateTextView = itemView.findViewById(R.id.state)
            itemView.tag=viewHolder

        }else{
            viewHolder = itemView.tag as ViewHolder
        }
        val user = getItem(position)
        viewHolder.nameTextView.text = user?.name
        viewHolder.stateTextView.text = user?.state
        if (viewHolder.stateTextView.text == "online"){
            viewHolder.stateTextView.setTextColor(greenColor)
        }else{
            viewHolder.stateTextView.setTextColor(redColor)
        }
        return itemView!!
    }
}