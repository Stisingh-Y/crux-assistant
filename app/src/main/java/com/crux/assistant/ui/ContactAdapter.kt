package com.crux.assistant.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.crux.assistant.data.Contact
import com.crux.assistant.databinding.ItemContactBinding

/**
 * ContactAdapter.kt
 *
 * Displays the manually-entered name -> number mappings (feature 4) with a delete button
 * per row. Nothing here talks to Android's Contacts provider — this list only ever shows
 * what the user typed in themselves.
 */
class ContactAdapter(
    private val onDelete: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    private var items: List<Contact> = emptyList()

    fun submitList(newItems: List<Contact>) {
        items = newItems
        notifyDataSetChanged() // list is tiny (a personal contact mapping) so simplicity wins here
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.nameText.text = contact.name
            binding.numberText.text = contact.phoneNumber
            binding.deleteButton.setOnClickListener { onDelete(contact) }
        }
    }
}
