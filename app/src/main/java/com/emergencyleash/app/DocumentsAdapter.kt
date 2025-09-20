package com.emergencyleash.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DocumentsAdapter(
    private val context: Context,
    private val documents: List<String>,
    private val onAddDocumentClick: () -> Unit
) : RecyclerView.Adapter<DocumentsAdapter.DocumentViewHolder>() {

    inner class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val documentNameTextView: TextView = itemView.findViewById(R.id.documentName)
        val documentIcon: ImageView = itemView.findViewById(R.id.documentIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_document, parent, false)
        return DocumentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        val documentUrl = documents[position]
        if (documentUrl == "add_document_placeholder") {
            holder.documentNameTextView.text = context.getString(R.string.add_document)
            holder.documentIcon.setImageResource(R.drawable.ic_add_document)
            holder.itemView.setOnClickListener {
                onAddDocumentClick()
            }
        } else {
            // Display the document name or a generic name
            val fileName = Uri.parse(documentUrl).lastPathSegment ?: "Document ${position + 1}"
            holder.documentNameTextView.text = fileName
            holder.documentIcon.setImageResource(R.drawable.ic_document)
            holder.itemView.setOnClickListener {
                // Handle document click (e.g., open document)
                openDocument(documentUrl)
            }
        }
    }

    override fun getItemCount(): Int {
        return documents.size
    }

    private fun openDocument(documentUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse(documentUrl), "*/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }
}
