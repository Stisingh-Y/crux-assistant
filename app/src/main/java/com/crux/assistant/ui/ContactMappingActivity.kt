package com.crux.assistant.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.crux.assistant.data.ContactStore
import com.crux.assistant.databinding.ActivityContactMappingBinding
import kotlinx.coroutines.launch

/**
 * ContactMappingActivity.kt
 *
 * The only non-voice screen in CRUX: a plain list of saved name -> number mappings plus
 * an add form. This exists specifically so CRUX can resolve names like "Amma" WITHOUT
 * requesting READ_CONTACTS — see data/ContactStore.kt for how/where this is persisted
 * (locally only, no network).
 */
class ContactMappingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactMappingBinding
    private lateinit var contactStore: ContactStore
    private lateinit var adapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactMappingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contactStore = ContactStore(this)

        adapter = ContactAdapter(onDelete = { contact ->
            lifecycleScope.launch { contactStore.remove(contact.name) }
        })
        binding.contactList.layoutManager = LinearLayoutManager(this)
        binding.contactList.adapter = adapter

        lifecycleScope.launch {
            contactStore.contacts.collect { list -> adapter.submitList(list) }
        }

        binding.addButton.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val number = binding.numberInput.text.toString().trim()
            if (name.isNotEmpty() && number.isNotEmpty()) {
                lifecycleScope.launch {
                    contactStore.upsert(name, number)
                    binding.nameInput.text?.clear()
                    binding.numberInput.text?.clear()
                }
            }
        }
    }
}
