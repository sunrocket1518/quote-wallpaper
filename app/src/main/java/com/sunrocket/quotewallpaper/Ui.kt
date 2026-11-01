package com.sunrocket.quotewallpaper

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner

/** 스피너에 항목을 넣고, 사용자가 다른 항목을 고를 때만 onSelect를 부름 */
fun Spinner.bind(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)
    setSelection(selected, false)
    var last = selected
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (position != last) {
                last = position
                onSelect(position)
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
}
