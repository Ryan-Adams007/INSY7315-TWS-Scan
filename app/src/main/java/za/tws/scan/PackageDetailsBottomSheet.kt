package za.tws.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import za.tws.scan.databinding.BottomsheetPackageDetailsBinding

class PackageDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetPackageDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetPackageDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Expand by default
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        // --- Read args (all optional) ---
        val pkgId = requireArguments().getString(ARG_PKG_ID, "PKG-000000")
        val destination = requireArguments().getString(ARG_DESTINATION, "TBD")
        val contact = requireArguments().getString(ARG_CONTACT, "—")
        val notes = requireArguments().getString(ARG_NOTES, "—")
        val status = requireArguments().getString(ARG_STATUS, "To Load")

        // --- Bind content ---
        binding.txtPkgId.text = pkgId
        binding.txtDestination.text = "Destination: $destination"
        binding.txtContact.text = "Contact: $contact"
        binding.txtNotes.text = "Notes: $notes"
        binding.chipStatus.text = status

        // Style status chip with your scheme
        when (status) {
            "Loaded" -> {
                binding.chipStatus.setChipStrokeColorResource(R.color.chipGreenBorder)
                binding.chipStatus.setChipBackgroundColorResource(R.color.chipGreenBackground)
                binding.chipStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.chipGreenText)
                )
            }
            else -> {
                binding.chipStatus.setChipStrokeColorResource(R.color.chipAmberBorder)
                binding.chipStatus.setChipBackgroundColorResource(R.color.chipAmberBackground)
                binding.chipStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.chipAmberText)
                )
            }
        }

        // Actions (UX only for now)
        binding.btnMarkLoaded.setOnClickListener {
            Toast.makeText(requireContext(), "Marked $pkgId as loaded", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        binding.btnOpenMaps.setOnClickListener {
            Toast.makeText(requireContext(), "Open maps for $destination…", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PKG_ID = "pkgId"
        private const val ARG_DESTINATION = "destination"
        private const val ARG_CONTACT = "contact"
        private const val ARG_NOTES = "notes"
        private const val ARG_STATUS = "status"

        fun newInstance(
            pkgId: String,
            destination: String,
            contact: String,
            notes: String,
            status: String
        ): PackageDetailsBottomSheet {
            val f = PackageDetailsBottomSheet()
            f.arguments = Bundle().apply {
                putString(ARG_PKG_ID, pkgId)
                putString(ARG_DESTINATION, destination)
                putString(ARG_CONTACT, contact)
                putString(ARG_NOTES, notes)
                putString(ARG_STATUS, status)
            }
            return f
        }
    }
}